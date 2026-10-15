package com.bbh.itss.dso.portal.application.catalog

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand
import com.bbh.itss.dso.portal.application.catalog.port.out.PipelineCountsPort
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductSummary
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelinesUseCase
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails
import com.bbh.itss.dso.portal.domain.catalog.ProductDirectory
import com.bbh.itss.dso.portal.domain.catalog.Service
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST
import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.dso.portal.support.CatalogFixtures.DEPARTMENT_ID
import static com.bbh.itss.dso.portal.support.Fixtures.account
import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.CatalogFixtures.details
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.settings
import static org.spockframework.mock.EmptyOrDummyResponse.INSTANCE

class ProductCatalogServiceSpec extends Specification {

    static final SonarSettings CERT_SONAR = SonarSettings.of(null, 'cert', command(['sonarqube']))
    static final Instant CHANGED = Instant.parse('2026-10-03T10:00:00Z')

    ProductRepositoryPort products = Mock(defaultResponse: INSTANCE)
    PipelineCountsPort pipelineCounts = Stub()
    PipelinesUseCase pipelines = Mock()
    def catalog = new ProductCatalogService(products, pipelineCounts, pipelines)

    def setup() {
        products.departmentExists(_) >> true
    }

    def "the list shows each product with its department, service and pipeline counts"() {
        given:
        products.summaries() >> [summary(1, 'CERT', 'CertScanner', 'TA', null, 3L, 'Corporate Technology'),
                                 summary(2, 'PAY', 'Payments Hub', null, null, null, null)]
        products.servicesPerProduct() >> [1L: 2L, 2L: 4L]
        pipelineCounts.pipelinesPerProduct() >> [1L: 3L]
        pipelineCounts.activePipelinesPerProduct() >> [1L: 1L]

        when:
        def list = catalog.list(null)

        then:
        list*.code() == ['CERT', 'PAY']
        list*.serviceCount() == [2, 4]
        list*.pipelineCount() == [3, 0]
        list*.activePipelineCount() == [1, 0]
        list[0].ownerTeam() == 'TA'
        list[0].id() == 1
        list[0].name() == 'CertScanner'
        list[0].updatedAt() == CHANGED
        list*.departmentId() == [3L, null]
        list*.departmentName() == ['Corporate Technology', null]
    }

    def "searching '#search' finds #codes"() {
        given:
        products.summaries() >> [summary(1, 'CERT', 'CertScanner', 'Technology Architecture', null, 3L,
                                         'Corporate Technology'),
                                 summary(2, 'PAY', 'Payments Hub', null, 'Payment orchestration', 5L, 'Fund Services')]

        expect:
        catalog.list(search)*.code() == codes

        where:
        search          || codes
        null            || ['CERT', 'PAY']
        ''              || ['CERT', 'PAY']
        '  '            || ['CERT', 'PAY']
        ' cert '        || ['CERT']
        'pay'           || ['PAY']
        'ARCHITECTURE'  || ['CERT']
        'orchestration' || ['PAY']
        'fund services' || ['PAY']
        'corporate'     || ['CERT']
        'nothing'       || []
    }

    def "a new product's code is suggested from its name, past the codes in use"() {
        given:
        products.findProductByCode('PAYMENTSHUB') >> Optional.of(new ProductDirectory.ProductIdentity(2, 'Payments Hub'))

        expect:
        catalog.suggestCode('Payments Hub') == 'PAYMENTSHUB2'
    }

    def "a product is returned with its services"() {
        given:
        products.load(5L) >> Optional.of(product(id: 5, services: [[name: 'gui', id: 10]]))

        expect:
        catalog.get(5L).services()[0].settings().metrics().influxProject() == 'CERT-gui'
    }

    def "an unknown product is not found, changed or deleted"() {
        when:
        action(catalog)

        then:
        def e = thrown(NoSuchElementException)
        e.message == 'Product 5 does not exist'
        0 * products.save(_)
        0 * products.delete(_)

        where:
        action << [{ it.get(5L) }, { it.update(5L, command()) }, { it.delete(5L) }]
    }

    def "a new product without services needs no AppScan account"() {
        when:
        def created = catalog.create(new ProductCommand(null, details(), null, [], null))

        then:
        1 * products.save({ Product p -> p.appScanAccount() == null && p.services().isEmpty() }) >> { Product p ->
            stored(9L, p)
        }
        1 * pipelines.createMissing(9L, [], FULL)
        created.id() == 9
    }

    def "a new product is stored with its services in order and gives every service a pipeline"() {
        given:
        def command = command(services: [service(name: 'gui'), service(name: 'backend-api')])

        when:
        def created = catalog.create(command)

        then:
        1 * products.save({ Product p -> p.id() == null && p.services()*.displayOrder() == [0, 1] }) >> { Product p ->
            stored(9L, p, [10L, 11L])
        }

        then:
        1 * pipelines.createMissing(9L, [10L, 11L], FULL)
        created.id() == 9
        created.services()*.name() == ['gui', 'backend-api']
        created.services()*.settings()*.metrics()*.influxProject() == ['CERT-gui', 'CERT-backend-api']
    }

    def "an update gives a pipeline only to the services it adds"() {
        given:
        products.load(5L) >> Optional.of(product(id: 5, services: [[name: 'gui', id: 10], [name: 'api', id: 11]]))

        when:
        catalog.update(5L, command(version: 0L, services: [service(id: 10L, name: 'gui')] + added))

        then:
        1 * products.save(_) >> { Product p -> stored(5L, p, [10L] + created) }
        1 * pipelines.createMissing(5L, created, FULL)

        where:
        added                                         || created
        [service(name: 'worker'), service(name: 'b')] || [12L, 13L]
        []                                            || []
    }

    def "a save naming the #type pipeline type gives that pipeline to every service of the product that lacks one"() {
        given:
        products.load(5L) >> Optional.of(product(id: 5, services: [[name: 'gui', id: 10]]))

        when:
        save(catalog, type)

        then:
        1 * products.save(_) >> { Product p -> stored(5L, p, [10L, 12L]) }
        1 * pipelines.createMissing(5L, [10L, 12L], type)

        where:
        [type, save] << [[SAST, NEXUS_IQ], [
                { ProductCatalogService saving, PipelineType pipelineType ->
                    saving.create(command(services: [service(name: 'gui'), service(name: 'worker')],
                            pipelineType: pipelineType))
                },
                { ProductCatalogService saving, PipelineType pipelineType ->
                    def services = [service(id: 10L, name: 'gui'), service(name: 'worker')]
                    saving.update(5L, command(version: 0L, services: services, pipelineType: pipelineType))
                }]].combinations()
    }

    def "every invalid service is reported at once and nothing is stored"() {
        given:
        def command = command(services: [
                service(name: 'gui', settings: settings(build: build(javaPath: null))),
                service(name: 'gui'),
                service(name: 'api', id: 77L),
                service(name: 'batch', settings: settings(sonar: CERT_SONAR))])

        when:
        catalog.create(command)

        then:
        def e = thrown(InvalidRequestException)
        e.problems()*.field == ['services[0].build.javaPath', 'services[1].name', 'services[2].id']
        0 * products.save(_)
    }

    def "an update replaces the details and the service list"() {
        given:
        products.load(5L) >> Optional.of(product(id: 5, services: [[name: 'gui', id: 10], [name: 'api', id: 11],
                                                                   [name: 'batch', id: 12]]))

        when:
        def updated = catalog.update(5L, command(version: 0L, name: 'CertScanner 2', services: [
                service(id: 11L, name: 'api', description: 'REST API'), service(name: 'worker'),
                service(id: 10L, name: 'web')]))

        then:
        1 * products.save({ Product p ->
            p.name() == 'CertScanner 2' && p.services()*.name() == ['api', 'worker', 'web'] &&
                    p.services()*.id() == [11L, null, 10L] && p.services()*.displayOrder() == [0, 1, 2]
        }) >> { Product p -> p }
        updated.services()[0].description() == 'REST API'
    }

    def "#refusal is refused before anything is stored"() {
        given:
        products.findProductByCode('CERT') >> Optional.of(new ProductDirectory.ProductIdentity(1L, 'Certificates'))
        products.load(1L) >> Optional.of(product(id: 1, version: 1))

        when:
        action(catalog)

        then:
        def e = thrown(IllegalStateException)
        e.message == message
        0 * products.save(_)

        where:
        refusal                              | action                                               || message
        'a product code in use'              | { it.create(command()) }                             || 'Product code CERT is already used by Certificates'
        'an update of an old version'        | { it.update(1L, command(version: 3L)) }              || STALE_VERSION
    }

    private static ProductSummary summary(long id, String code, String name, String ownerTeam, String description,
                                          Long departmentId, String departmentName) {
        new ProductSummary(id, code, name, description, ownerTeam, departmentId, departmentName, CHANGED)
    }

    private static ServiceDraft service(Map args) {
        new ServiceDraft(args.id as Long, args.name as String, args.description as String,
                args.settings ?: settings())
    }

    private static ProductCommand command(Map args = [:]) {
        new ProductCommand(args.version as Long, details(args), account(),
                args.services as List<ServiceDraft> ?: [service(name: 'gui')], args.pipelineType as PipelineType)
    }

    private static Product stored(long id, Product product, List<Long> serviceIds = []) {
        List<Service> services = product.services().withIndex().collect { Service service, int index ->
            new Service(index < serviceIds.size() ? serviceIds[index] : service.id(), service.name(),
                    service.description(), service.displayOrder(), service.settings())
        }
        Product.restore(id, product.details(), product.appScanAccount(), services, 0, CHANGED, CHANGED)
    }
}
