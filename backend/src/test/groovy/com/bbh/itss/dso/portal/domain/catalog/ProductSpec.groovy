package com.bbh.itss.dso.portal.domain.catalog

import com.bbh.itss.dso.portal.domain.catalog.ProductDirectory.ProductIdentity
import com.bbh.itss.dso.portal.domain.shared.ConfigTree
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.TestStage.SMOKE
import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.dso.portal.support.CatalogFixtures.DEPARTMENT_ID
import static com.bbh.itss.dso.portal.support.Fixtures.account
import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.CatalogFixtures.details
import static com.bbh.itss.dso.portal.support.Fixtures.directory
import static com.bbh.itss.dso.portal.support.Fixtures.draft
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.service
import static com.bbh.itss.dso.portal.support.Fixtures.settings

class ProductSpec extends Specification {

    static final SonarSettings CERT_SONAR = SonarSettings.of(null, 'cert', ToolCommand.of(['sonarqube'], []))
    static final MetricsSettings SHARED_METRICS = new MetricsSettings(true, 'CertScanner', 'test', null, null)

    def nobody = directory()

    def "product details trim the code and the name and store blank values as null"() {
        expect:
        new ProductDetails(' CERT ', ' CertScanner ', ' ', ' Technology Architecture ', '', 3L) ==
                new ProductDetails('CERT', 'CertScanner', null, 'Technology Architecture', null, 3L)
        new ProductDetails(null, null, null, null, null, null).code() == null
    }

    def "a new product places its services in the requested order and names their metrics after itself"() {
        when:
        def product = Product.create(details(description: ' TLS '), account(),
                [draft(name: ' gui ', description: ' '), draft(name: 'backend-api', description: 'REST API')], nobody)

        then:
        [product.id(), product.createdAt(), product.updatedAt(), product.ownerTeam(), product.contactEmail()] ==
                [null] * 5
        product.version() == 0
        product.details() == details(description: 'TLS')
        product.departmentId() == DEPARTMENT_ID
        product.appScanAccount() == account()
        product.services()*.name() == ['gui', 'backend-api']
        product.services()*.displayOrder() == [0, 1]
        product.services()*.description() == [null, 'REST API']
        product.services()*.id() == [null, null]
        product.services()*.settings()*.metrics()*.influxProject() == ['CERT-gui', 'CERT-backend-api']
        product.serviceIds() == [] as Set
        Product.create(details(), account(), [draft(metrics: new MetricsSettings(true, 'cert-scanner', 'uat', null, null))], nobody)
                .services()[0].settings().metrics() == new MetricsSettings(true, 'cert-scanner', 'uat', null, null)
    }

    def "a stored product lists its services by display order and name, finds them by id and keeps the list to itself"() {
        given:
        def product = product(id: 5, services: [[name: 'gui', id: 10, displayOrder: 2], [name: 'backend-api', id: 11, displayOrder: 1],
                                                [name: 'batch', id: 12, displayOrder: 1]])

        expect:
        product.id() == 5
        product.services()*.name() == ['backend-api', 'batch', 'gui']
        product.service(11L).get().name() == 'backend-api'
        product.service(99L).isEmpty()
        product.service(null).isEmpty()
        product.serviceIds() == [11L, 12L, 10L] as Set
        product.details() == details()

        when:
        product.services().add(service())

        then:
        thrown(UnsupportedOperationException)
    }

    def "a service writes its config.yaml entry with the product's AppScan account"() {
        given:
        def product = product(services: [[name: 'gui', id: 10, testJobs: [
                TestJob.of(SMOKE, 'smoke', null, 'CERT/gui-smoke', null)]]])
        def tree = new ConfigTree()

        when:
        product.writeConfig(product.services()[0], tree)

        then:
        tree.get('buildTool') == 'gradle'
        tree.get('tests.smoke.jobs') == [[name: 'smoke', job: 'CERT/gui-smoke']]
        tree.get('asoc') == [keyId: 'bbh_key-id', token: 'hcl-app-scan-account']
        tree.get('influx.project') == 'CERT-gui'
        tree.get('appId') == '109f44ac-cc06-4ca0-884e-d944904f7019'
    }

    def "an update replaces the details and the service list: kept services change, new ones come, missing ones go"() {
        given:
        def product = product(id: 5, services: [[name: 'gui', id: 10], [name: 'api', id: 11], [name: 'batch', id: 12]])
        def newAccount = new AppScanAccount('bbh_other', null)

        when:
        product.update(0L, details(name: 'CertScanner 2'), newAccount, [
                draft(id: 11, name: 'api', description: 'REST API'),
                draft(name: 'worker'),
                draft(id: 10, name: 'web')], nobody)

        then:
        product.id() == 5
        product.name() == 'CertScanner 2'
        product.appScanAccount() == newAccount
        product.services()*.name() == ['api', 'worker', 'web']
        product.services()*.id() == [11L, null, 10L]
        product.services()*.displayOrder() == [0, 1, 2]
        product.services()[0].description() == 'REST API'
        product.services()*.settings()*.metrics()*.influxProject() == ['CERT-api', 'CERT-worker', 'CERT-web']
    }

    def "an update that sends no version is not checked against the stored one"() {
        given:
        def product = product(version: 4, services: [[name: 'gui', id: 10]])

        when:
        product.update(null, details(), account(), [draft(id: 10, name: 'gui')], nobody)

        then:
        product.version() == 4
    }

    def "an update based on an older version is refused before anything changes"() {
        given:
        def product = product(version: 4, services: [[name: 'gui', id: 10]])

        when:
        product.update(3L, details(name: 'Other'), account(), [], nobody)

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION
        product.name() == 'CertScanner'
        product.services()*.name() == ['gui']
    }

    def "a product #value used by another product is refused"() {
        when:
        Product.create(details(), account(), [draft()], directory(lookup))

        then:
        def e = thrown(IllegalStateException)
        e.message == message

        where:
        value  | lookup                                                         || message
        'code' | [byCode: [CERT: new ProductIdentity(1, 'Certificates')]]       || 'Product code CERT is already used by Certificates'
        'name' | [byName: [CertScanner: new ProductIdentity(1, 'certscanner')]] || 'A product named certscanner already exists'
    }

    def "every invalid service is reported at once"() {
        when:
        Product.create(details(), account(), [
                draft(name: 'gui', build: build(javaPath: null)),
                draft(name: 'GUI '),
                draft(name: 'api', id: 77)], nobody)

        then:
        def e = thrown(InvalidRequestException)
        e.problems()*.field == ['services[0].build.javaPath', 'services[1].name', 'services[2].id']
        e.problems()*.message == ['set the JDK path or enable automatic build tool setup, the unit tests stage needs one of them',
                                'another service of this product already uses this name',
                                'service 77 does not belong to this product']
    }

    def "services may share metrics tags and a SonarQube key, within the product and with other products"() {
        when:
        def product = Product.create(details(), account(), [draft(name: 'gui', sonar: CERT_SONAR, metrics: SHARED_METRICS),
                                                            draft(name: 'api', sonar: CERT_SONAR, metrics: SHARED_METRICS)],
                nobody)

        then:
        product.services()*.settings()*.sonar()*.projectKey() == ['cert', 'cert']
        product.services()*.settings()*.metrics()*.influxProject() == ['CertScanner', 'CertScanner']
    }

    def "a product's own code and name do not clash with themselves on update"() {
        given:
        def product = product(id: 5, services: [[name: 'gui', id: 10, sonar: CERT_SONAR]])
        def itself = new ProductIdentity(5, 'CertScanner')

        when:
        product.update(0L, details(), account(), [draft(id: 10, name: 'gui', sonar: CERT_SONAR)],
                directory(byCode: [CERT: itself], byName: [CertScanner: itself]))

        then:
        noExceptionThrown()
    }

    def "a product needs a code, a name, a department and named services, and an AppScan key once it has services"() {
        when:
        Product.create(new ProductDetails(' ', null, null, null, null, null), new AppScanAccount(' ', null),
                [draft(name: ' ')], nobody)

        then:
        def e = thrown(InvalidRequestException)
        e.problems()*.field == ['code', 'name', 'departmentId', 'appScan.keyId', 'services[0].name']
        e.problems()*.message.unique() == ['must not be blank', "choose the product's department"]

        when:
        Product.create(details(), null, [draft()], nobody)

        then:
        def missing = thrown(InvalidRequestException)
        missing.problems()*.field == ['appScan.keyId']

        when:
        product(appScan: null).update(0L, details(), new AppScanAccount(' ', 'hcl-app-scan-account'), [draft()],
                nobody)

        then:
        def added = thrown(InvalidRequestException)
        added.problems()*.field == ['appScan.keyId']
    }

    def "texts that fit their column in characters but not in UTF-8 bytes are refused on their fields"() {
        when:
        Product.create(details(name: 'Ł' * 101, description: 'ą' * 2001, ownerTeam: 'ś' * 101), account(),
                [draft(description: 'ż' * 1001)], nobody)

        then:
        def e = thrown(InvalidRequestException)
        e.problems()*.field == ['name', 'description', 'ownerTeam']
        e.problems()*.message == ['is too long: it may take at most 200 bytes',
                                  'is too long: it may take at most 4000 bytes',
                                  'is too long: it may take at most 200 bytes']

        when:
        Product.create(details(), account(), [draft(name: 'gui'), draft(name: 'api', description: '–' * 667)], nobody)

        then:
        def service = thrown(InvalidRequestException)
        service.problems()*.field == ['services[1].description']
        service.problems()*.message == ['is too long: it may take at most 2000 bytes']

        when:
        def fitting = Product.create(details(name: 'Ł' * 100, description: 'ą' * 2000, ownerTeam: 'ś' * 100),
                account(), [draft(description: '–' * 666)], nobody)

        then:
        fitting.description().length() == 2000
        fitting.services()[0].description().length() == 666
    }

    def "a product without services needs no AppScan key: #account"() {
        when:
        def product = Product.create(details(), account, [], nobody)

        then:
        product.appScanAccount() == account
        product.services().isEmpty()

        where:
        account << [null, new AppScanAccount(' ', null), new AppScanAccount('bbh_key-id', null)]
    }

    def "a product without a department or in one that does not exist is refused, also when an older product is edited: #departmentId"() {
        given:
        def older = product(departmentId: null, services: [[name: 'gui', id: 10]])

        when:
        Product.create(details(departmentId: departmentId), account(), [draft()], nobody)

        then:
        def created = thrown(InvalidRequestException)
        created.problems()*.field == ['departmentId']
        created.problems()*.message == [message]

        when:
        older.update(0L, details(departmentId: departmentId), account(), [draft(id: 10, name: 'gui')], nobody)

        then:
        def updated = thrown(InvalidRequestException)
        updated.problems() == created.problems()
        older.departmentId() == null

        when:
        older.update(0L, details(), account(), [draft(id: 10, name: 'gui')], nobody)

        then:
        older.departmentId() == DEPARTMENT_ID

        where:
        departmentId || message
        null         || "choose the product's department"
        99L          || 'department 99 does not exist'
    }

    def "a service and a draft trim their name and store a blank description as null"() {
        expect:
        new Service(3L, ' gui ', ' ', 1, settings()) == new Service(3L, 'gui', null, 1, settings())
        new ServiceDraft(null, ' gui ', ' ', settings()) == new ServiceDraft(null, 'gui', null, settings())
        new Service(null, null, null, 0, settings()).name() == null
        new ServiceDraft(null, null, null, settings()).name() == null
        service(id: 3).hasId(3L)
        !service(id: 3).hasId(4L)
        !service(id: null).hasId(null)
    }

    def "a service and a draft cannot do without their settings"() {
        when:
        factory()

        then:
        def e = thrown(NullPointerException)
        e.message == 'a service needs its settings'

        where:
        factory << [{ new Service(1L, 'gui', null, 0, null) }, { new ServiceDraft(1L, 'gui', null, null) }]
    }
}
