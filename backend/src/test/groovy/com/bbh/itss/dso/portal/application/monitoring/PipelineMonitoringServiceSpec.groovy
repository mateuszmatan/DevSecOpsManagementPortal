package com.bbh.itss.dso.portal.application.monitoring

import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringStatus
import com.bbh.itss.dso.portal.application.monitoring.port.out.DashboardLinksPort
import com.bbh.itss.dso.portal.application.monitoring.port.out.PipelineRunsPort
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.monitoring.DashboardLink
import com.bbh.itss.dso.portal.domain.monitoring.DoraPoint
import com.bbh.itss.dso.portal.domain.monitoring.LatestRuns
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun
import com.bbh.itss.dso.portal.domain.monitoring.RunResult
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.DISABLED
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.FAILURE
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.NO_DATA
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.SUCCESS
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.UNSTABLE
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.revokedKey
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings
import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC
import static org.spockframework.mock.EmptyOrDummyResponse.INSTANCE

class PipelineMonitoringServiceSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-04T12:00:00Z')
    static final String NOT_CONFIGURED = 'InfluxDB is not configured for the portal'

    ProductRepositoryPort products = Mock(defaultResponse: INSTANCE)
    PipelineRepositoryPort pipelines = Mock(defaultResponse: INSTANCE)
    PipelineRunsPort runs = Mock()
    DashboardLinksPort dashboards = Mock(defaultResponse: INSTANCE)
    ManageGlobalSettingsUseCase settings = Stub() {
        current() >> storedSettings('https://jenkins.test')
    }

    def targets = new MonitoringTargetsService(products, pipelines, settings)
    def monitoring = new PipelineMonitoringService(targets, runs, dashboards, fixed(NOW, UTC))

    Product certScanner = product(id: 1L, code: 'CERT', name: 'CertScanner', ownerTeam: 'TA',
            services: [[name: 'gui', id: 10L], [name: 'backend-api', id: 11L]])
    Pipeline guiFull = pipeline(id: 100L, serviceId: 10L, jenkinsJob: 'DevSecOps/CERT/gui-full')
    Pipeline guiSast = pipeline(id: 101L, serviceId: 10L, type: SAST, keys: [revokedKey(reason: 'retired')])
    Pipeline apiFull = pipeline(id: 102L, serviceId: 11L)
    Product payments = product(id: 2L, code: 'PAY', name: 'Payments Hub', services: [[name: 'gateway', id: 20L]])
    Pipeline gatewayFull = pipeline(id: 200L, productId: 2L, serviceId: 20L)

    def setup() {
        products.load(1L) >> Optional.of(certScanner)
    }

    def "the status says whether InfluxDB answers and which Grafana instances there are"() {
        given:
        runs.configured() >> configured
        runs.ping() >> { if (failure) { throw new UncheckedIOException(failure, new IOException()) } }
        dashboards.instances() >> grafana

        expect:
        monitoring.status() == new MonitoringStatus(configured, reachable, failure, grafana)

        where:
        configured | failure                         | grafana                                                                                              || reachable
        false      | null                            | []                                                                                                   || false
        true       | null                            | [new DashboardLink('Test', 'https://grafana.bbh.com'), new DashboardLink('Prod', 'https://grafana-prod.bbh.com')] || true
        true       | 'InfluxDB could not be read: x' | []                                                                                                   || false
    }

    def "the overview rates each product by its worst pipeline"() {
        given:
        def success = run('2026-10-03T10:00:00Z', SUCCESS)
        def failure = run('2026-10-04T09:00:00Z', FAILURE)
        def empty = product(id: 3L, code: 'EMPTY', name: 'Empty')
        products.findAll() >> [certScanner, empty, payments]
        pipelines.findAll() >> [guiFull, guiSast, apiFull, gatewayFull]

        when:
        def overview = monitoring.overview()

        then:
        1 * runs.latestRuns({ it.size() == 4 }, [] as Set) >> latest([(tag(guiFull)): success, (tag(apiFull)): failure])
        overview.metricsError() == null
        overview.products()*.product()*.code() == ['CERT', 'EMPTY', 'PAY']
        with(overview.products()[0]) {
            overall() == FAILURE
            statusCounts() == [(SUCCESS): 1, (FAILURE): 1, (DISABLED): 1]
            lastRunAt() == failure.time()
            pipelineCount() == 3
        }
        overview.products()[0].product().is(certScanner)
        with(overview.products()[1]) {
            overall() == NO_DATA
            statusCounts() == [:]
            pipelineCount() == 0
            lastRunAt() == null
        }
        overview.products()[2].overall() == NO_DATA
    }

    def "without InfluxDB the overview still lists every pipeline and leaves out those whose product is gone"() {
        given:
        products.findAll() >> [certScanner]
        pipelines.findAll() >> [guiFull, guiSast, apiFull, gatewayFull]

        when:
        def overview = monitoring.overview()

        then:
        1 * runs.latestRuns([tag(guiFull), tag(guiSast), tag(apiFull)] as Set, _) >> {
            throw new UncheckedIOException(NOT_CONFIGURED, new IOException())
        }
        overview.metricsError() == NOT_CONFIGURED
        overview.products()[0].statusCounts() == [(NO_DATA): 2, (DISABLED): 1]
        overview.products()[0].pipelineCount() == 3
    }

    def "without any pipeline the overview reports only what the metrics store says"() {
        given:
        products.findAll() >> [certScanner]

        when:
        def overview = monitoring.overview()

        then:
        1 * runs.latestRuns([] as Set, _) >> { if (failure) { throw failure }; LatestRuns.none() }
        overview.metricsError() == error

        where:
        failure                                                     || error
        null                                                        || null
        new UncheckedIOException(NOT_CONFIGURED, new IOException()) || NOT_CONFIGURED
    }

    def "a product shows the status and last run of each pipeline"() {
        given:
        def unstable = run('2026-10-04T09:00:00Z', UNSTABLE)
        pipelines.findByProductId(1L) >> [guiFull, guiSast]
        runs.latestRuns(*_) >> latest([(tag(guiFull)): unstable])

        when:
        def product = monitoring.product(1L)

        then:
        product.product().code() == 'CERT'
        product.overall() == UNSTABLE
        product.pipelines()*.status() == [UNSTABLE, DISABLED]
        product.pipelines()[0].lastRun() == unstable
        product.pipelines()[0].pipeline().pipeline().id() == 100
        product.pipelines()[0].pipeline().jenkinsJobUrl() == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/'
        product.pipelines()[1].lastRun() == null
        product.pipelines()[1].pipeline().jenkinsJobUrl() == null
        product.metricsError() == null
    }

    def "a product whose runs cannot be read reports why and an unknown product is not found"() {
        given:
        pipelines.findByProductId(1L) >> [guiFull]
        runs.latestRuns(*_) >> {
            throw new UncheckedIOException('InfluxDB could not be read: timeout', new IOException())
        }

        when:
        def product = monitoring.product(1L)

        then:
        product.pipelines()*.status() == [NO_DATA]
        product.overall() == NO_DATA
        product.metricsError() == 'InfluxDB could not be read: timeout'

        when:
        monitoring.product(9L)

        then:
        thrown(NoSuchElementException)
    }

    def "a department lists the status and last run of every pipeline of its products, also when the runs cannot be read"() {
        given:
        def failed = run('2026-10-04T08:00:00Z', FAILURE)
        products.departmentExists(3L) >> true
        products.findByDepartmentId(3L) >> [certScanner, payments]
        pipelines.findByDepartmentId(3L) >> [guiFull, guiSast, gatewayFull]
        runs.latestRuns(*_) >>> [latest([(MetricsTag.of(payments.services()[0], gatewayFull)): failed])] >> {
            throw new UncheckedIOException(NOT_CONFIGURED, new IOException())
        }

        when:
        def department = monitoring.department(3L)

        then:
        department.pipelines()*.pipeline()*.pipeline()*.id() == [100L, 101L, 200L]
        department.pipelines()*.status() == [NO_DATA, DISABLED, FAILURE]
        department.pipelines()[2].lastRun() == failed
        department.pipelines()[2].pipeline().product().code() == 'PAY'
        department.metricsError() == null

        when:
        def unread = monitoring.department(3L)

        then:
        unread.pipelines()*.status() == [NO_DATA, DISABLED, NO_DATA]
        unread.metricsError() == NOT_CONFIGURED
    }

    def "a pipeline's details show its runs, DORA metrics and Grafana dashboard"() {
        given:
        def tag = tag(guiFull)
        def newest = run('2026-10-04T09:00:00Z', SUCCESS)
        pipelines.load(100L) >> Optional.of(guiFull)

        when:
        def details = monitoring.pipeline(100L, '30d')

        then:
        1 * runs.recentRuns(tag, null, 30, 25) >> [newest, run('2026-10-03T09:00:00Z', FAILURE)]
        1 * runs.doraPoints([tag], 30) >> [(tag): [new DoraPoint(newest.time(), true, false, 3600, 600)]]
        1 * dashboards.dashboards(tag, guiFull.type(), 30) >> [new DashboardLink('Test', 'https://grafana/d/x'), new DashboardLink('Prod', 'https://grafana-prod/d/x')]
        0 * runs.latestRuns(*_)
        details.pipeline().pipeline().keys().size() == 1
        details.pipeline().jenkinsJobUrl() == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/'
        details.status() == SUCCESS
        details.lastRun() == newest
        details.recentRuns().size() == 2
        details.dora().runs() == 1
        details.dora().daily().size() == 30
        details.dora().daily().last().date().toString() == '2026-10-04'
        details.dashboards() == [new DashboardLink('Test', 'https://grafana/d/x'), new DashboardLink('Prod', 'https://grafana-prod/d/x')]
        details.metricsError() == null
    }

    def "without runs in the range the last run before it is shown"() {
        given:
        def old = run('2026-05-01T09:00:00Z', FAILURE)
        pipelines.load(100L) >> Optional.of(guiFull)
        runs.recentRuns(*_) >> []
        runs.doraPoints(*_) >> [:]

        when:
        def details = monitoring.pipeline(100L, '7d')

        then:
        1 * runs.latestRuns([tag(guiFull)] as Set, [] as Set) >> latest([(tag(guiFull)): old])
        details.lastRun() == old
        details.status() == FAILURE
        details.dora().runs() == 0
        details.dashboards() == []
    }

    def "when the last run before the range cannot be read the pipeline says why instead of reporting no runs"() {
        given:
        pipelines.load(100L) >> Optional.of(guiFull)
        runs.recentRuns(*_) >> []
        runs.doraPoints(*_) >> [:]
        runs.latestRuns(*_) >> { throw new UncheckedIOException('InfluxDB could not be read: timeout', new IOException()) }

        when:
        def details = monitoring.pipeline(100L, '7d')

        then:
        details.lastRun() == null
        details.metricsError() == 'InfluxDB could not be read: timeout'
    }

    def "when the runs cannot be read the DORA query is skipped and the Grafana dashboard is still shown"() {
        given:
        pipelines.load(101L) >> Optional.of(guiSast)
        runs.recentRuns(*_) >> { throw new UncheckedIOException(NOT_CONFIGURED, new IOException()) }
        dashboards.dashboards(_, _, 90) >> [new DashboardLink('Grafana', 'https://grafana/d/x')]

        when:
        def details = monitoring.pipeline(101L, '90d')

        then:
        0 * runs.doraPoints(*_)
        0 * runs.latestRuns(*_)
        details.metricsError() == NOT_CONFIGURED
        details.status() == DISABLED
        details.recentRuns() == []
        details.dora().runs() == 0
        details.dashboards() == [new DashboardLink('Grafana', 'https://grafana/d/x')]
    }

    def "when only the DORA points cannot be read the runs are still shown"() {
        given:
        def newest = run('2026-10-04T09:00:00Z', SUCCESS)
        pipelines.load(100L) >> Optional.of(guiFull)
        runs.recentRuns(*_) >> [newest]
        runs.doraPoints(*_) >> { throw new UncheckedIOException('InfluxDB could not be read: dora', new IOException()) }

        when:
        def details = monitoring.pipeline(100L, '30d')

        then:
        details.lastRun() == newest
        details.status() == SUCCESS
        details.dora().runs() == 0
        details.metricsError() == 'InfluxDB could not be read: dora'
    }

    def "an unknown pipeline or one whose product is gone is not found"() {
        given:
        pipelines.load(200L) >> Optional.of(gatewayFull)

        when:
        monitoring.pipeline(id, '30d')

        then:
        def e = thrown(NoSuchElementException)
        e.message == message

        where:
        id   || message
        999L || 'Pipeline 999 does not exist'
        200L || 'Product 2 does not exist'
    }

    def "services sharing a tag see the latest run of their own Jenkins job, and a pipeline without a job sees none"() {
        given:
        Product shared = product(id: 3L, code: 'CERT', name: 'CertScanner', services: [
                [name: 'gui', id: 10L, metrics: new MetricsSettings(true, 'CertScanner', 'test', null, null)],
                [name: 'backend-api', id: 11L, metrics: new MetricsSettings(true, 'CertScanner', 'test', null, null)],
                [name: 'batch', id: 12L, metrics: new MetricsSettings(true, 'CertScanner', 'test', null, null)]])
        Pipeline gui = pipeline(id: 100L, productId: 3L, serviceId: 10L, jenkinsJob: 'DevSecOps/CERT/gui-full')
        Pipeline api = pipeline(id: 102L, productId: 3L, serviceId: 11L,
                jenkinsJob: 'https://jenkins.test/job/DevSecOps/job/CERT/job/api-full/')
        Pipeline batch = pipeline(id: 103L, productId: 3L, serviceId: 12L)
        def tag = new MetricsTag('CertScanner', 'test')
        def guiRun = run('2026-10-04T08:00:00Z', SUCCESS, 'DevSecOps/CERT/gui-full/develop')
        def apiRun = run('2026-10-04T09:00:00Z', FAILURE, 'DevSecOps/CERT/api-full')
        products.load(3L) >> Optional.of(shared)
        pipelines.findByProductId(3L) >> [gui, api, batch]
        pipelines.sharedMetricsTags() >> ([tag] as Set)

        when:
        def product = monitoring.product(3L)

        then:
        1 * runs.latestRuns([tag] as Set, [tag] as Set) >> new LatestRuns([(tag): [apiRun, guiRun]], [tag] as Set)
        product.pipelines()*.lastRun() == [guiRun, apiRun, null]
        product.pipelines()*.status() == [SUCCESS, FAILURE, NO_DATA]
    }

    def "a pipeline of a shared tag lists only the runs of its Jenkins job, and none without a job"() {
        given:
        pipelines.load(id) >> Optional.of(id == 100L ? guiFull : apiFull)
        pipelines.sharedMetricsTags() >> ([tag(guiFull), tag(apiFull)] as Set)

        when:
        def details = monitoring.pipeline(id, '30d')

        then:
        queries * runs.recentRuns(_, job, 30, 25) >> [run('2026-10-04T09:00:00Z', SUCCESS, 'DevSecOps/CERT/gui-full')]
        queries * runs.doraPoints(*_) >> [:]
        details.recentRuns().size() == queries

        where:
        id   || job                       | queries
        100L || 'DevSecOps/CERT/gui-full' | 1
        102L || null                      | 0
    }

    def "the portfolio activity adds up the DORA points of every pipeline"() {
        given:
        products.findAll() >> [certScanner, payments]
        pipelines.findAll() >> [guiFull, guiSast, apiFull, gatewayFull]

        when:
        def activity = monitoring.activity('7d')

        then:
        1 * runs.doraPoints({ it.size() == 4 }, 7) >> [
                (tag(guiFull)): [new DoraPoint(NOW.minusSeconds(86_400), true, false, 3600, 600)],
                (tag(apiFull)): [new DoraPoint(NOW.minusSeconds(3600), true, true, 7200, 900),
                                 new DoraPoint(NOW.minusSeconds(1800), false, false, 0, 300)]]
        activity.pipelines() == 4
        activity.metricsError() == null
        with(activity.dora()) {
            runs() == 3
            deployments() == 2
            daily().size() == 7
            daily().last().runs() == 2
        }
    }

    def "when the DORA points cannot be read the portfolio activity says why"() {
        given:
        products.findAll() >> [certScanner]
        pipelines.findAll() >> [guiFull]
        runs.doraPoints(*_) >> { throw new UncheckedIOException(NOT_CONFIGURED, new IOException()) }

        when:
        def activity = monitoring.activity('30d')

        then:
        activity.pipelines() == 1
        activity.metricsError() == NOT_CONFIGURED
        activity.dora().runs() == 0
    }

    def "a range that is not a number of days is refused before anything is read"() {
        when:
        monitoring.pipeline(100L, '30h')

        then:
        thrown(InvalidRequestException)
        0 * pipelines._
        0 * runs._
    }

    private static PipelineRun run(String time, RunResult result, String job = null) {
        new PipelineRun(Instant.parse(time), result, 'develop', 1, 600, null, job, null, null, null, null, null, null)
    }

    private static LatestRuns latest(Map<MetricsTag, PipelineRun> runs, Set<MetricsTag> shared = [] as Set) {
        new LatestRuns(runs.collectEntries { tag, run -> [tag, [run]] }, shared)
    }

    private MetricsTag tag(Pipeline pipeline) {
        MetricsTag.of(certScanner.service(pipeline.service().serviceId()).get(), pipeline)
    }
}
