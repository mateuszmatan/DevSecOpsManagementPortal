package com.bbh.itss.dso.portal.application.pipeline

import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.application.settings.port.in.ManageServiceTemplateUseCase
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag
import com.bbh.itss.dso.portal.domain.pipeline.IssuedKey
import com.bbh.itss.dso.portal.domain.pipeline.KeyGenerator
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.settings.StoredServiceTemplate
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.pipeline.KeyStatus.ACTIVE
import static com.bbh.itss.dso.portal.domain.pipeline.KeyStatus.REVOKED
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY
import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.dso.portal.support.Fixtures.KEY
import static com.bbh.itss.dso.portal.support.Fixtures.activeKey
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.pipelineSettings
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.revokedKey
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings
import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC
import static org.spockframework.mock.EmptyOrDummyResponse.INSTANCE

class PipelineServiceSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-05T07:30:00.123456789Z')
    static final Instant NOW_MICROS = Instant.parse('2026-10-05T07:30:00.123456Z')
    static final String NEW_KEY = '9b2e2a52-2f0d-4f53-9b55-4f0b9a8f6c11'

    PipelineRepositoryPort pipelines = Mock(defaultResponse: INSTANCE)
    ProductRepositoryPort products = Mock(defaultResponse: INSTANCE)
    ManageGlobalSettingsUseCase settings = Stub() {
        current() >> storedSettings('https://jenkins.test')
    }
    KeyGenerator keys = { -> NEW_KEY } as KeyGenerator
    def template = StoredServiceTemplate.unsaved()
    ManageServiceTemplateUseCase templates = Stub() {
        current() >> { template }
    }
    def service = new PipelineService(pipelines, products, settings, templates, keys, fixed(NOW, UTC))

    def certScanner = product(id: 1, services: [[name: 'gui', id: 10], [name: 'backend-api', id: 11]])

    def "each service of a product is listed with its pipelines, and one pipeline comes with its product and service"() {
        given:
        products.load(1L) >> Optional.of(certScanner)
        pipelines.findByProductId(1L) >> [pipeline(id: 100), pipeline(id: 101, type: SAST)]
        pipelines.load(100L) >> Optional.of(pipeline(id: 100, keys: [activeKey(), revokedKey()]))

        when:
        def list = service.listForProduct(1L)
        def view = service.get(100L)

        then:
        list*.service()*.name() == ['gui', 'backend-api']
        list[0].pipelines()*.pipeline()*.id() == [100, 101]
        list[0].pipelines()*.jenkinsUrl() == ['https://jenkins.test', 'https://jenkins.test']
        list[1].pipelines() == []
        [view.product().code(), view.service().name(), view.pipeline().keys().size(), view.jenkinsUrl()] ==
                ['CERT', 'gui', 2, 'https://jenkins.test']
        view.metricsTag() == new MetricsTag('CERT-gui', 'test')
    }

    def "#action finds no #missing"() {
        given:
        pipelines.load(101L) >> Optional.of(pipeline(id: 101, productId: 2))
        products.load(1L) >> Optional.of(certScanner)
        products.findByServiceId(12L) >> Optional.of(certScanner)

        when:
        call(service)

        then:
        def e = thrown(NoSuchElementException)
        e.message == "$missing does not exist"
        0 * pipelines.save(_)
        0 * pipelines.delete(_)

        where:
        action                  | missing        | call
        'listing'               | 'Product 2'    | { it.listForProduct(2L) }
        'reading'               | 'Pipeline 100' | { it.get(100L) }
        'reading'               | 'Product 2'    | { it.get(101L) }
        'starting new services' | 'Service 12'   | { it.createMissing(1L, [12L], FULL) }
        'starting new services' | 'Product 7'    | { it.createMissing(7L, [10L], FULL) }
        'creating'              | 'Service 10'   | { it.create(10L, FULL, pipelineSettings()) }
        'creating'              | 'Service 12'   | { it.create(12L, FULL, pipelineSettings()) }
        'deleting'              | 'Pipeline 100' | { it.delete(100L) }
        'issuing a key'         | 'Pipeline 100' | { it.issueKey(100L) }
    }

    def "a pipeline is created with its first key"() {
        given:
        products.findByServiceId(10L) >> Optional.of(certScanner)

        when:
        def view = service.create(10L, SECURITY, pipelineSettings(
                agentLabels: ['linux'], extendedPipelineJob: 'CERT/gui-extended', jenkinsJob: 'DevSecOps/CERT/gui-security'))

        then:
        1 * pipelines.save({ Pipeline p ->
            p.id() == null && p.type() == SECURITY && p.service().serviceId() == 10L &&
                    p.service().productId() == 1L && p.keys()*.value() == [NEW_KEY] && p.keys()[0].issuedAt() == NOW_MICROS
        }) >> { Pipeline p -> stored(100L, p) }
        view.pipeline().id() == 100
        view.pipeline().settings().extendedPipelineJob() == 'CERT/gui-extended'
        view.jenkinsJobUrl() == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-security/'
        view.pipeline().activeKey().get().status() == ACTIVE
    }

    def "every service a save covers starts with a #type pipeline and a key, named by the service template"() {
        given:
        products.load(1L) >> Optional.of(certScanner)

        when:
        def views = service.createMissing(1L, [10L, 11L], type)

        then:
        1 * pipelines.save({ Pipeline p ->
            p.type() == type && p.settings().agentLabels() == ['linux-agent'] &&
                    p.settings().jenkinsJob() == "DevSecOps/CERT/gui-$slug" && p.keys()*.value() == [NEW_KEY]
        }) >> stored(100L, pipeline(id: null, serviceId: 10L))
        1 * pipelines.save({ Pipeline p -> p.settings().jenkinsJob() == "DevSecOps/CERT/backend-api-$slug" }) >>
                stored(101L, pipeline(id: null, serviceId: 11L))
        views*.pipeline()*.id() == [100L, 101L]
        views*.service()*.name() == ['gui', 'backend-api']
        views.every { it.pipeline().isEnabled() }

        where:
        type     | slug
        FULL     | 'full'
        SAST     | 'sast'
        NEXUS_IQ | 'nexusiq'
    }

    def "a saved service template decides the agents and the job of the pipelines the portal starts"() {
        given:
        products.load(1L) >> Optional.of(certScanner)
        template = new StoredServiceTemplate(StoredServiceTemplate.unsaved().template().toBuilder()
                .agentLabels(['linux && docker', 'windows-agent']).jenkinsJob(job).build(), 3, null)

        when:
        service.createMissing(1L, [10L], SECURITY)

        then:
        1 * pipelines.save({ Pipeline p ->
            p.settings().agentLabels() == ['linux && docker', 'windows-agent'] && p.settings().jenkinsJob() == expected
        }) >> { Pipeline p -> stored(100L, p) }

        where:
        job                              | expected
        'Teams/{code}/{service}/{type}'  | 'Teams/cert/gui/security'
        null                             | null
    }

    def "a service that already has a pipeline of the type keeps it, and a save that covered no service reads nothing"() {
        given:
        products.load(1L) >> Optional.of(certScanner)
        pipelines.existsForService(10L, SECURITY) >> true

        when:
        def views = service.createMissing(1L, [10L, 11L], SECURITY)

        then:
        1 * pipelines.save({ Pipeline p -> p.service().serviceId() == 11L }) >> { Pipeline p -> stored(101L, p) }
        0 * pipelines.save({ Pipeline p -> p.service().serviceId() == 10L })
        views*.pipeline()*.id() == [101L]

        when:
        def none = service.createMissing(1L, [], SECURITY)

        then:
        none == []
        0 * products.load(_)
        0 * pipelines.save(_)
    }

    def "#refusal is refused before anything is saved"() {
        given:
        products.findByServiceId(10L) >> Optional.of(certScanner)
        pipelines.existsForService(10L, FULL) >> true
        pipelines.load(100L) >> Optional.of(pipeline(id: 100, version: 4))

        when:
        action(service)

        then:
        def e = thrown(IllegalStateException)
        e.message == message
        0 * pipelines.save(_)

        where:
        refusal                       | action                                              || message
        'a second pipeline of a type' | { it.create(10L, FULL, pipelineSettings()) }        || 'Service gui already has a full pipeline'
        'a change of the type'        | { it.update(100L, 4L, SAST, pipelineSettings()) }   || 'The type of a pipeline cannot change; add a new pipeline instead'
        'a change read at version 3'  | { it.update(100L, 3L, FULL, pipelineSettings()) }   || STALE_VERSION
        'a change read at version 5'  | { it.update(100L, 5L, FULL, pipelineSettings()) }   || STALE_VERSION
    }

    def "a pipeline's settings can change, at the version they were read at or without one"() {
        given:
        pipelines.load(100L) >> Optional.of(pipeline(id: 100, version: 4))
        products.load(1L) >> Optional.of(certScanner)

        when:
        def view = service.update(100L, version, FULL, pipelineSettings(
                agentLabels: ['windows', 'linux'], extendedPipelineJob: 'CERT/x', securityPipelineJob: 'CERT/y',
                description: 'Nightly'))

        then:
        1 * pipelines.save({ Pipeline p -> p.settings().agentLabels() == ['windows', 'linux'] }) >> { Pipeline p -> p }
        view.pipeline().settings().description() == 'Nightly'
        view.pipeline().settings().extendedPipelineJob() == null
        view.pipeline().settings().securityPipelineJob() == null

        where:
        version << [4L, null]
    }

    def "a key is revoked on the pipeline loaded under a row lock"() {
        given:
        products.load(1L) >> Optional.of(certScanner)

        when:
        def view = service.revokeKey(100L, 'Leaked in a build log')

        then:
        1 * pipelines.loadForUpdate(100L) >> Optional.of(pipeline(id: 100))
        0 * pipelines.load(_)

        then:
        1 * pipelines.save({ Pipeline p -> !p.enabled }) >> { Pipeline p -> p }
        !view.pipeline().enabled
        view.pipeline().keys()[0].revokeReason() == 'Leaked in a build log'
        view.pipeline().keys()[0].revokedAt() == NOW_MICROS
    }

    def "a new key is issued on the pipeline loaded under a row lock"() {
        given:
        products.load(1L) >> Optional.of(certScanner)

        when:
        def view = service.issueKey(100L)

        then:
        1 * pipelines.loadForUpdate(100L) >> Optional.of(pipeline(id: 100))

        then:
        1 * pipelines.save(_) >> { Pipeline p -> p }
        view.pipeline().activeKey().get().value() == NEW_KEY
        view.pipeline().keys()*.status() == [ACTIVE, REVOKED]
    }

    def "an active key given in any case and with spaces authorizes its pipeline and its use is recorded"() {
        when:
        long pipelineId = service.authorizeKey(" ${KEY.toUpperCase()} ")

        then:
        1 * pipelines.findKey(KEY) >> Optional.of(new IssuedKey(100L, activeKey(id: 500L)))
        1 * pipelines.recordKeyUse(500L, NOW_MICROS) >> true
        0 * pipelines.load(_)
        0 * pipelines.save(_)
        0 * products._
        pipelineId == 100L
    }

    def "a revoked key is refused with the reason and its use is not recorded"() {
        given:
        def revoked = revokedKey(reason: 'Service retired')
        pipelines.findKey(revoked.value()) >> Optional.of(new IssuedKey(100L, revoked))

        when:
        service.authorizeKey(revoked.value())

        then:
        def e = thrown(SecurityException)
        e.message == "The DevSecOps pipeline key was invalidated on ${revoked.revokedAt()}: Service retired"
        0 * pipelines.recordKeyUse(*_)
    }

    def "a key whose use was not recorded is refused when it was #outcome meanwhile"() {
        when:
        service.authorizeKey(KEY)

        then:
        2 * pipelines.findKey(KEY) >>> [Optional.of(new IssuedKey(100L, activeKey(id: 500L))), reread]
        1 * pipelines.recordKeyUse(500L, NOW_MICROS) >> false
        def e = thrown(failure)
        e.message.endsWith(message)

        where:
        outcome       | reread                                                                     || failure                | message
        'invalidated' | Optional.of(new IssuedKey(100L, revokedKey(value: KEY, reason: 'Leaked'))) || SecurityException      | ': Leaked'
        'deleted'     | Optional.empty()                                                           || NoSuchElementException | 'Unknown DevSecOps pipeline key'
    }

    def "a key that is still active after a use that was not recorded authorizes its pipeline"() {
        when:
        long pipelineId = service.authorizeKey(KEY)

        then:
        2 * pipelines.findKey(KEY) >> Optional.of(new IssuedKey(100L, activeKey(id: 500L)))
        1 * pipelines.recordKeyUse(500L, NOW_MICROS) >> false
        pipelineId == 100L
    }

    def "a key that was never issued is not found: '#value'"() {
        when:
        service.authorizeKey(value)

        then:
        def e = thrown(NoSuchElementException)
        e.message == 'Unknown DevSecOps pipeline key'
        0 * pipelines.recordKeyUse(*_)

        where:
        value << ['00000000-0000-4000-8000-000000000000', ' ', null]
    }

    private static Pipeline stored(long id, Pipeline pipeline) {
        Pipeline.restore(id, pipeline.service(), pipeline.type(), pipeline.settings(),
                pipeline.keys().collect { k -> activeKey(id: 500L, value: k.value(), issuedAt: k.issuedAt()) },
                0, NOW, NOW)
    }
}
