package com.bbh.itss.dso.portal.application.monitoring

import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringTargets
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings
import static org.spockframework.mock.EmptyOrDummyResponse.INSTANCE

class MonitoringTargetsServiceSpec extends Specification {

    ProductRepositoryPort products = Mock(defaultResponse: INSTANCE)
    PipelineRepositoryPort pipelines = Mock(defaultResponse: INSTANCE)
    ManageGlobalSettingsUseCase settings = Stub() {
        current() >> storedSettings('https://jenkins.test')
    }

    @Subject
    def targets = new MonitoringTargetsService(products, pipelines, settings)

    def certScanner = product(id: 1L, services: [[name: 'gui', id: 10L], [name: 'backend-api', id: 11L]])
    def payments = product(id: 2L, services: [[name: 'gateway', id: 20L]])
    def guiFull = pipeline(id: 100L, serviceId: 10L, jenkinsJob: 'DevSecOps/CERT/gui-full')
    def apiFull = pipeline(id: 102L, serviceId: 11L)
    def gatewayFull = pipeline(id: 200L, productId: 2L, serviceId: 20L)
    def orphan = pipeline(id: 300L, productId: 9L, serviceId: 90L)

    def "every product and its pipelines are read with the Jenkins the links need, leaving out pipelines whose product is gone"() {
        given:
        products.findAll() >> [certScanner, payments]
        pipelines.findAll() >> [guiFull, apiFull, gatewayFull, orphan]

        when:
        def read = targets.everything()

        then:
        read.products() == [certScanner, payments]
        read.pipelines()*.pipeline()*.id() == [100L, 102L, 200L]
        read.pipelines()*.service()*.name() == ['gui', 'backend-api', 'gateway']
        read.platform().jenkinsUrl() == 'https://jenkins.test'
        read.pipelines()[0].jenkinsJobUrl() == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/'
    }

    def "one product is read with its own pipelines only, and one pipeline with the product it belongs to"() {
        given:
        products.load(1L) >> Optional.of(certScanner)
        pipelines.findByProductId(1L) >> [guiFull, apiFull]
        pipelines.load(100L) >> Optional.of(guiFull)

        when:
        def product = targets.ofProduct(1L)
        def pipeline = targets.ofPipeline(100L)

        then:
        product.product().is(certScanner)
        product.pipelines()*.pipeline()*.id() == [100L, 102L]
        0 * pipelines.findAll()
        pipeline.pipeline().pipeline().is(guiFull)
        pipeline.pipeline().service().name() == 'gui'
        pipeline.product().is(certScanner)
    }

    def "one department is read with the products and pipelines it holds only"() {
        given:
        products.departmentExists(3L) >> true
        products.findByDepartmentId(3L) >> [certScanner]
        pipelines.findByDepartmentId(3L) >> [guiFull, apiFull]

        when:
        def read = targets.ofDepartment(3L)

        then:
        read.products() == [certScanner]
        read.pipelines()*.pipeline()*.id() == [100L, 102L]
        read.pipelines()[0].jenkinsJobUrl() == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/'
        0 * products.findAll()
        0 * pipelines.findAll()
    }

    def "every read names the tags that pipelines of several services write under"() {
        given:
        def shared = [new MetricsTag('CertScanner', 'test')] as Set
        products.findAll() >> [certScanner]
        products.load(1L) >> Optional.of(certScanner)
        pipelines.load(100L) >> Optional.of(guiFull)
        pipelines.sharedMetricsTags() >> shared

        expect:
        targets.everything().sharedTags() == shared
        targets.ofProduct(1L).sharedTags() == shared
        targets.ofPipeline(100L).sharedTags() == shared
        targets.ofPipeline(100L).tags() == [MetricsTag.of(certScanner.services()[0], guiFull)] as Set
    }

    def "an unknown #what is reported as not found"() {
        given:
        pipelines.load(100L) >> Optional.of(guiFull)

        when:
        read(targets)

        then:
        def e = thrown(NoSuchElementException)
        e.message == message

        where:
        what                   | message                        | read
        'department'           | 'Department 7 does not exist'  | { MonitoringTargetsService it -> it.ofDepartment(7L) }
        'product'              | 'Product 5 does not exist'     | { MonitoringTargetsService it -> it.ofProduct(5L) }
        'pipeline'             | 'Pipeline 101 does not exist'  | { MonitoringTargetsService it -> it.ofPipeline(101L) }
        "pipeline's product"   | 'Product 1 does not exist'     | { MonitoringTargetsService it -> it.ofPipeline(100L) }
    }

    def "targets of many products are no targets of one product or one pipeline, and always name their platform"() {
        given:
        products.findAll() >> [certScanner, payments]
        pipelines.findAll() >> [guiFull, gatewayFull]

        when:
        read(targets.everything())

        then:
        def e = thrown(failure)
        e.message == message

        where:
        read << [{ MonitoringTargets it -> it.product() }, { MonitoringTargets it -> it.pipeline() },
                 { MonitoringTargets it -> new MonitoringTargets([], [], null, [] as Set) }]
        failure << [IllegalArgumentException, IllegalArgumentException, NullPointerException]
        message << ['these monitoring targets are not those of one product',
                    'these monitoring targets are not those of one pipeline',
                    'monitoring targets carry the platform settings their links are built on']
    }
}
