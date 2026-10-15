package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.PortalSpecification
import com.zaxxer.hikari.HikariDataSource

import java.time.Instant

import static com.bbh.itss.dso.portal.support.ApiJson.pipeline
import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ApiJson.service
import static java.time.Duration.ofDays
import static java.time.Duration.ofHours

class MonitoringRegressionSpec extends PortalSpecification {

    static final String PIPELINE_DASHBOARD = 'http://grafana.test/d/adzfc54123/devsecops-pipeline-long?orgId=1'
    static final String SECURITY_DASHBOARD = 'http://grafana.test/d/ad2trcm/devsecops-security'
    static final String PROD_DASHBOARD = 'http://grafana-prod.test/d/adzfc54123/devsecops-pipeline-long'

    String code
    Map monitored
    Map guiFull
    Map guiSast
    Map apiFull

    def setup() {
        influx.reset()
        code = uniqueCode('MON')
        monitored = createProduct(product(code: code, name: "Monitored $code", services: [service(name: 'gui'), service(name: 'api')]))
        guiFull = pipelineFor(monitored.services[0].id as long)
        guiSast = pipelineFor(monitored.services[0].id as long, pipeline(type: 'SAST'))
        apiFull = pipelineFor(monitored.services[1].id as long)
        api.post("/api/pipelines/$guiSast.id/keys/revoke", [reason: 'paused'])

        def now = Instant.now()
        influx.addRun(project: "$code-gui", time: now - ofDays(3), result: 'FAILURE', deployment: true,
                leadTimeSeconds: 7200)
        influx.addRun(project: "$code-gui", time: now - ofDays(2), result: 'SUCCESS', leadTimeSeconds: 3600)
        influx.addRun(project: "$code-gui", time: now - ofHours(4), result: 'FAILURE', leadTimeSeconds: 600)
        influx.addRun(project: "$code-gui", time: now - ofHours(1), result: 'SUCCESS', leadTimeSeconds: 1800, build: 42)
        influx.addRun(project: "$code-api", time: now - ofHours(2), result: 'FAILURE')
    }

    def cleanup() {
        influx.reset()
    }

    def "the status shows InfluxDB is reachable and which Grafana instances there are"() {
        when:
        def status = api.get('/api/monitoring/status').json

        then:
        status == [influxConfigured: true, influxReachable: true, influxError: null,
                   grafana: [[name: 'Grafana test', dashboardUrl: PIPELINE_DASHBOARD],
                             [name: 'Grafana prod', dashboardUrl: PROD_DASHBOARD]]]
        influx.requests.last().authorization == 'Token test-token'
    }

    def "the overview rates the product by its worst pipeline"() {
        when:
        def overview = api.get('/api/monitoring/products').json
        def health = overview.products.find { it.code == code }

        then:
        overview.metricsError == null
        health.overall == 'FAILURE'
        health.statusCounts == [SUCCESS: 1, FAILURE: 1, DISABLED: 1]
        health.pipelineCount == 3
        health.serviceCount == 2
        health.lastRunAt != null
    }

    def "a product shows the status and last run of each pipeline"() {
        when:
        def monitoring = api.get("/api/monitoring/products/$monitored.id").json

        then:
        monitoring.overall == 'FAILURE'
        monitoring.pipelines*.pipeline*.id == [guiFull.id, guiSast.id, apiFull.id]
        monitoring.pipelines*.status == ['SUCCESS', 'DISABLED', 'FAILURE']
        monitoring.pipelines[0].lastRun.build == 42
        monitoring.pipelines[1].lastRun == null
    }

    def "a department lists every pipeline of its products with its status, last run and masked key"() {
        given:
        def department = api.post('/api/departments', [name: "Pipelines $code".toString()]).json
        def other = createProduct(product(code: "${code}D", name: "Department $code", departmentId: department.id,
                services: [service(name: 'worker')]))
        def moved = api.put("/api/products/$monitored.id", product(code: code, name: monitored.name,
                version: monitored.version, services: monitored.services, departmentId: department.id))

        when:
        def listed = api.get("/api/pipelines?departmentId=$department.id").json

        then:
        moved.status == 200
        listed.metricsError == null
        listed.pipelines*.pipeline*.productCode == [other.code, code, code, code]
        listed.pipelines*.pipeline*.serviceName == ['worker', 'gui', 'gui', 'api']
        listed.pipelines*.pipeline*.type == ['FULL', 'FULL', 'SAST', 'FULL']
        listed.pipelines*.status == ['NO_DATA', 'SUCCESS', 'DISABLED', 'FAILURE']
        listed.pipelines[1].lastRun.build == 42
        listed.pipelines[1].pipeline.activeKey.value == null
        listed.pipelines[1].pipeline.activeKey.hint.endsWith(guiFull.activeKey.value[-4..-1])
        api.get('/api/pipelines?departmentId=999999').status == 404
        api.get('/api/pipelines').status == 400
    }

    def "a pipeline's details hold its runs, DORA metrics and Grafana dashboard"() {
        when:
        def details = api.get("/api/monitoring/pipelines/$guiFull.id?range=30d").json

        then:
        details.metricsError == null
        details.status == 'SUCCESS'
        details.recentRuns*.result == ['SUCCESS', 'FAILURE', 'SUCCESS', 'FAILURE']
        details.recentRuns*.buildUrl.every { it == null }
        details.pipeline.keys == []
        details.pipeline.activeKey.value == null
        details.pipeline.activeKey.hint == "${guiFull.activeKey.value.take(8)}\u2026${guiFull.activeKey.value[-4..-1]}"
        with(details.dora) {
            runs == 4
            deployments == 3
            Math.abs(changeFailureRatePercent - 33.333) < 0.01
            changeFailureRateLevel == 'LOW'
            restores == 1
            meanTimeToRestoreSeconds == 86_400
            timeToRestoreLevel == 'MEDIUM'
            leadTimeMedianSeconds == 3600
            failingSince == null
            daily.size() == 30
        }
        details.grafana == [[name: 'Grafana test', dashboardUrl: "$PIPELINE_DASHBOARD&var-project=$code-gui&from=now-30d&to=now".toString()],
                            [name: 'Grafana prod', dashboardUrl: "$PROD_DASHBOARD?var-project=$code-gui&from=now-30d&to=now".toString()]]
    }

    def "the activity of all pipelines adds up their DORA points"() {
        when:
        def activity = api.get('/api/monitoring/activity?range=7d').json

        then:
        activity.metricsError == null
        activity.pipelines >= 3
        activity.dora.runs == 5
        activity.dora.deployments == 3
        activity.dora.daily.size() == 7
        activity.dora.daily*.runs.sum() == 5
    }

    def "security, SAST and Nexus IQ GoldenFix pipelines link the security dashboard of every instance that has one"() {
        given:
        def guiNexusIq = pipelineFor(monitored.services[0].id as long, pipeline(type: 'NEXUS_IQ'))

        when:
        def details = api.get("/api/monitoring/pipelines/$guiSast.id?range=7d").json
        def nexusIq = api.get("/api/monitoring/pipelines/$guiNexusIq.id?range=7d").json

        then:
        details.grafana == [[name: 'Grafana test', dashboardUrl: "$SECURITY_DASHBOARD?var-project=$code-guisast&from=now-7d&to=now".toString()],
                            [name: 'Grafana prod', dashboardUrl: "$PROD_DASHBOARD?var-project=$code-guisast&from=now-7d&to=now".toString()]]
        nexusIq.grafana*.dashboardUrl == ["$SECURITY_DASHBOARD?var-project=$code-guinexusiq&from=now-7d&to=now".toString(),
                                          "$PROD_DASHBOARD?var-project=$code-guinexusiq&from=now-7d&to=now".toString()]
    }

    def "a pipeline without runs in the range shows the last one before it"() {
        given:
        def apiSast = pipelineFor(monitored.services[1].id as long, pipeline(type: 'SAST'))
        influx.addRun(project: "$code-apisast", variant: 'sast', time: Instant.now() - ofDays(60), result: 'UNSTABLE')

        when:
        def details = api.get("/api/monitoring/pipelines/$apiSast.id?range=7d").json

        then:
        details.recentRuns == []
        details.lastRun.result == 'UNSTABLE'
        details.status == 'UNSTABLE'
        details.dora.runs == 0
    }

    def "when InfluxDB fails the pages still list every pipeline"() {
        given:
        influx.failWith(500)

        when:
        def status = api.get('/api/monitoring/status').json
        def overview = api.get('/api/monitoring/products').json
        def details = api.get("/api/monitoring/pipelines/$guiFull.id").json
        def activity = api.get('/api/monitoring/activity').json

        then:
        activity.metricsError.startsWith('InfluxDB could not be read')
        activity.dora.runs == 0
        !status.influxReachable
        status.influxError.startsWith('InfluxDB could not be read: 500')
        overview.metricsError.startsWith('InfluxDB could not be read')
        overview.products.find { it.code == code }.statusCounts == [NO_DATA: 2, DISABLED: 1]
        details.status == 'NO_DATA'
        details.metricsError.startsWith('InfluxDB could not be read')
        details.grafana*.name == ['Grafana test', 'Grafana prod']
        details.grafana[0].dashboardUrl.startsWith(PIPELINE_DASHBOARD)
    }

    def "InfluxDB is queried while the portal holds no database connection"() {
        given:
        def pool = (jdbc.dataSource as HikariDataSource).hikariPoolMXBean
        List<Integer> busy = [].asSynchronized()
        influx.onQuery { busy << pool.activeConnections }

        when:
        def answers = ['/api/monitoring/status', '/api/monitoring/products', "/api/monitoring/products/$monitored.id",
                       "/api/monitoring/pipelines/$guiFull.id"]
                .collect { api.get(it as String).status }

        then:
        answers.every { it == 200 }
        busy.size() >= 5
        busy.every { it == 0 }
    }

    def "a range that is not a number of days is refused"() {
        expect:
        api.get("/api/monitoring/pipelines/$guiFull.id?range=1y").status == 400
        api.get('/api/monitoring/activity?range=0d').status == 400
        api.get('/api/monitoring/pipelines/999999').status == 404
    }
}
