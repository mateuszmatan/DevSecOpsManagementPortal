package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ProductDirectory.ProductIdentity
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.Region.QC
import static com.bbh.itss.dso.portal.domain.catalog.Region.RD
import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.dso.portal.support.CatalogFixtures.DEPARTMENT_ID
import static com.bbh.itss.dso.portal.support.Fixtures.account
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.CatalogFixtures.copy
import static com.bbh.itss.dso.portal.support.CatalogFixtures.details
import static com.bbh.itss.dso.portal.support.Fixtures.fullSettings
import static com.bbh.itss.dso.portal.support.Fixtures.settings
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:product-adapter;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Import(ProductPersistenceAdapter)
class ProductPersistenceAdapterSpec extends Specification {

    @Autowired
    ProductPersistenceAdapter adapter

    @Autowired
    TestEntityManager entities

    @Autowired
    JdbcTemplate jdbc

    def "a product with every section of every service is stored and read back unchanged"() {
        given:
        def created = Product.create(details(description: 'Scans certificates', ownerTeam: 'TA',
                contactEmail: 'ta@bbh.com'), account(), [new ServiceDraft(null, 'gui', 'Angular', fullSettings('gui')),
                                                         new ServiceDraft(null, 'api', null, settings())], adapter)

        when:
        def saved = adapter.save(created)
        entities.clear()
        def loaded = adapter.load(saved.id()).get()

        then:
        saved.version() == 0
        saved.updatedAt() == saved.createdAt()
        [loaded.version(), loaded.createdAt()] == [saved.version(), saved.createdAt()]
        loaded.details() == created.details()
        loaded.appScanAccount() == account()
        loaded.services() == saved.services()
        loaded.services()*.description() == ['Angular', null]
        loaded.services()*.displayOrder() == [0, 1]
        loaded.services()[0].settings() == fullSettings('gui')
        loaded.services()[1].settings() == settings(metrics: new MetricsSettings(true, 'CERT-api', 'test', null, null))
        loaded.services()[0].settings().sshTargets().keySet() as List == [RD, QC]
    }

    def "a product without services is stored and read back without an AppScan account"() {
        when:
        def saved = adapter.save(Product.create(details(), null, [], adapter))
        entities.clear()

        then:
        adapter.load(saved.id()).get().appScanAccount() == null
        jdbc.queryForMap('SELECT ASOC_KEY_ID, ASOC_SECRET_CREDENTIALS_ID FROM DSO_PRODUCT WHERE ID = ?', saved.id()) ==
                [ASOC_KEY_ID: null, ASOC_SECRET_CREDENTIALS_ID: null]
    }

    def "the columns hold the values the library view and the old rows use"() {
        when:
        def saved = adapter.save(Product.create(details(), account(), [new ServiceDraft(null, 'gui', null,
                fullSettings('gui'))], adapter))
        def serviceId = saved.services()[0].id()

        then:
        jdbc.queryForMap('SELECT CODE, NAME, ASOC_KEY_ID, ASOC_SECRET_CREDENTIALS_ID, DEPARTMENT_ID FROM DSO_PRODUCT') ==
                [CODE: 'CERT', NAME: 'CertScanner', ASOC_KEY_ID: 'bbh_key-id',
                 ASOC_SECRET_CREDENTIALS_ID: 'hcl-app-scan-account', DEPARTMENT_ID: DEPARTMENT_ID]
        jdbc.queryForMap('''SELECT BUILD_TOOL, BUILD_TASKS, DELIVERY_TASKS, BITBUCKET_REVIEWERS, GOLDEN_FIX_ENABLED,
                INFLUX_PROJECT, REGRESSION_REQUIRED, BUILD_RETURN_STDOUT, NEXUS_IQ_SERVER_URL FROM DSO_SERVICE WHERE ID = ?''',
                serviceId) ==
                [BUILD_TOOL: 'MAVEN', BUILD_TASKS: 'build-task\nsecond', DELIVERY_TASKS: 'delivery-task\nsecond',
                 BITBUCKET_REVIEWERS: 'alice,bob', GOLDEN_FIX_ENABLED: 0, INFLUX_PROJECT: 'cert-gui', REGRESSION_REQUIRED: 0,
                 BUILD_RETURN_STDOUT: 1, NEXUS_IQ_SERVER_URL: 'https://iq.cert.bbh.com']
        jdbc.queryForList('''SELECT APPLICATION, SCAN_PATTERNS, STAGE, FAIL_ON_NETWORK_ERROR FROM DSO_SERVICE_NEXUS_IQ_APP
                WHERE SERVICE_ID = ? ORDER BY POSITION''', serviceId) ==
                [[APPLICATION: 'cert-gui', SCAN_PATTERNS: '**/*.war\n**/*.jar', STAGE: 'release', FAIL_ON_NETWORK_ERROR: 1],
                 [APPLICATION: 'cert-gui-batch', SCAN_PATTERNS: '**/batch/*.jar', STAGE: 'build', FAIL_ON_NETWORK_ERROR: 0]]
        jdbc.queryForMap('''SELECT BITBUCKET_API_URL, BITBUCKET_WORKSPACE, BITBUCKET_PROJECT_KEY, BITBUCKET_REPO_SLUG
                FROM DSO_SERVICE WHERE ID = ?''', serviceId) ==
                [BITBUCKET_API_URL    : 'https://bitbucket.bbh.com/rest/api/1.0', BITBUCKET_WORKSPACE: 'ta-workspace',
                 BITBUCKET_PROJECT_KEY: 'TA', BITBUCKET_REPO_SLUG: 'cert-gui']
        jdbc.queryForList('SELECT POSITION, STAGE FROM DSO_SERVICE_TEST_JOB WHERE SERVICE_ID = ? ORDER BY POSITION',
                serviceId)*.STAGE == ['SMOKE', 'REGRESSION', 'PERFORMANCE']
        jdbc.queryForList('SELECT POSITION, APPLICATION_NAME FROM DSO_UCD_APPLICATION WHERE SERVICE_ID = ? ORDER BY POSITION',
                serviceId)*.APPLICATION_NAME == ['Cert', 'Cert Batch']
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_UCD_COMPONENT', Integer) == 3
        jdbc.queryForList('SELECT REGION FROM DSO_SERVICE_SSH_TARGET WHERE SERVICE_ID = ? ORDER BY REGION',
                serviceId)*.REGION == ['QC', 'RD']
        jdbc.queryForList('SELECT REGION FROM DSO_SERVICE_OPENSHIFT_TARGET WHERE SERVICE_ID = ? ORDER BY REGION',
                serviceId)*.REGION == ['QC', 'RD']
    }

    def "an update replaces the details and the service list, keeping the ids of the services it keeps"() {
        given:
        def stored = adapter.save(Product.create(details(), account(), [draft('gui'), draft('api'), draft('batch')],
                adapter))
        def (gui, api, batch) = stored.services()*.id()

        when:
        stored.update(stored.version(), details(name: 'CertScanner 2', ownerTeam: 'Architecture', departmentId: 5L),
                account(), [new ServiceDraft(api, 'api', 'REST API', settings()),
                            new ServiceDraft(null, 'batch', null, settings()), new ServiceDraft(gui, 'web', null, settings())],
                adapter)
        def saved = adapter.save(stored)
        entities.clear()
        def loaded = adapter.load(stored.id()).get()

        then:
        saved.version() > stored.version()
        loaded.version() == saved.version()
        loaded.name() == 'CertScanner 2'
        loaded.ownerTeam() == 'Architecture'
        loaded.departmentId() == 5L
        adapter.summaries()[0].departmentName() == 'Fund Services'
        loaded.services()*.name() == ['api', 'batch', 'web']
        loaded.services()*.displayOrder() == [0, 1, 2]
        loaded.services()[0].id() == api
        loaded.services()[0].description() == 'REST API'
        loaded.services()[1].id() !in [gui, api, batch]
        loaded.services()[2].id() == gui
        loaded.services()[2].settings().metrics().influxProject() == 'CERT-web'
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_SERVICE', Integer) == 3
    }

    def "changed lists of a kept service replace the stored rows and empty lists remove them"() {
        given:
        def created = adapter.save(Product.create(details(), account(), [new ServiceDraft(null, 'gui', null,
                fullSettings('gui'))], adapter))
        entities.clear()
        def stored = adapter.load(created.id()).get()
        def gui = stored.services()[0].id()
        def changed = fullSettings('gui')
        def trimmed = copy(changed, testJobs: changed.testJobs().take(1), openShiftTargets: [:],
                urbanCodeApplications: changed.urbanCodeApplications().drop(1), sshTargets: [(QC): changed.sshTargets()[QC]])

        when:
        stored.update(null, details(), account(), [new ServiceDraft(gui, 'gui', null, trimmed)], adapter)
        adapter.save(stored)
        entities.clear()

        then:
        adapter.load(stored.id()).get().services()[0].settings() == trimmed
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_SERVICE_TEST_JOB', Integer) == 1
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_UCD_APPLICATION', Integer) == 1
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_UCD_COMPONENT', Integer) == 1
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_SERVICE_SSH_TARGET', Integer) == 1
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_SERVICE_OPENSHIFT_TARGET', Integer) == 0
    }

    def "every update raises the version and the modification time, also one that changes only a service"() {
        given:
        def stored = adapter.save(Product.create(details(), account(), [draft('gui'), draft('api')], adapter))
        def (gui, api) = stored.services()*.id()

        when:
        stored.update(stored.version(), details(), account(),
                [new ServiceDraft(gui, 'gui', 'Edited', settings()), draft('api', api)], adapter)
        def edited = adapter.save(stored)
        edited.update(edited.version(), details(), account(),
                [new ServiceDraft(gui, 'gui', 'Edited', settings()), draft('api', api)], adapter)
        def unchanged = adapter.save(edited)

        then:
        edited.version() > stored.version()
        edited.updatedAt().isAfter(stored.updatedAt())
        edited.services()[0].description() == 'Edited'
        unchanged.version() > edited.version()
        unchanged.updatedAt().isAfter(edited.updatedAt())
        jdbc.queryForObject('SELECT VERSION FROM DSO_PRODUCT', Long) == unchanged.version()
    }

    def "names, SonarQube keys and metrics tags move and swap between the services of one save"() {
        given:
        def stored = adapter.save(Product.create(details(), account(), [
                draft('gui', null, tagged('cert-gui')), draft('api', null, tagged('cert-api'))], adapter))
        def (gui, api) = stored.services()*.id()

        when:
        stored.update(stored.version(), details(), account(), [
                draft('api', gui, tagged('cert-api')), draft('gui-legacy', api, tagged('cert-gui')), draft('gui')],
                adapter)
        adapter.save(stored)
        entities.clear()
        def loaded = adapter.load(stored.id()).get()

        then:
        loaded.services()*.id().take(2) == [gui, api]
        loaded.services()*.name() == ['api', 'gui-legacy', 'gui']
        loaded.services()*.settings()*.sonar()*.projectKey() == ['cert-api', 'cert-gui', null]
        loaded.services()*.settings()*.metrics()*.influxProject() == ['cert-api', 'cert-gui', 'CERT-gui']
        jdbc.queryForObject("SELECT COUNT(*) FROM DSO_SERVICE WHERE NAME LIKE '~%' OR INFLUX_PROJECT LIKE '~%'",
                Integer) == 0
    }

    def "a product #change is not written"() {
        given:
        def stored = adapter.save(Product.create(details(), account(), [draft('gui')], adapter))
        def stale = Product.restore(id ?: stored.id(), details(name: 'Stale'), account(), id ? [] : stored.services(),
                stored.version() + 3, stored.createdAt(), stored.updatedAt())

        when:
        adapter.save(stale)

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION
        jdbc.queryForObject('SELECT NAME FROM DSO_PRODUCT', String) == 'CertScanner'

        where:
        change                                | id
        'read at another version than stored' | null
        'deleted in the meantime'             | 404L
    }

    def "products are listed by name with their summaries, departments and service counts"() {
        given:
        def payments = adapter.save(Product.create(details(code: 'PAY', name: 'Payments Hub', description: 'Payments',
                departmentId: 5L), account(), [draft('gateway'), draft('ledger'), draft('mobile')], adapter))
        def cert = adapter.save(Product.create(details(ownerTeam: 'TA'), account(), [draft('gui')], adapter))
        def empty = adapter.save(Product.create(details(code: 'EMPTY', name: 'Empty'), account(), [], adapter))
        jdbc.update('UPDATE DSO_PRODUCT SET DEPARTMENT_ID = NULL WHERE ID = ?', empty.id())
        entities.clear()

        expect:
        adapter.findAll()*.code() == ['CERT', 'EMPTY', 'PAY']
        adapter.findAll()[2].services()*.name() == ['gateway', 'ledger', 'mobile']
        adapter.findByDepartmentId(5L)*.code() == ['PAY']
        adapter.findByDepartmentId(DEPARTMENT_ID)*.code() == ['CERT']
        adapter.findByDepartmentId(9999L) == []
        adapter.summaries()*.code() == ['CERT', 'EMPTY', 'PAY']
        with(adapter.summaries()[0]) {
            id() == cert.id()
            name() == 'CertScanner'
            ownerTeam() == 'TA'
            description() == null
            updatedAt() == cert.updatedAt()
            departmentId() == DEPARTMENT_ID
            departmentName() == 'Corporate Technology'
        }
        adapter.summaries()[2].description() == 'Payments'
        adapter.summaries()*.departmentName() == ['Corporate Technology', null, 'Fund Services']
        adapter.summaries()*.departmentId() == [DEPARTMENT_ID, null, 5L]
        adapter.load(empty.id()).get().departmentId() == null
        adapter.servicesPerProduct() == [(payments.id()): 3L, (cert.id()): 1L]
        !adapter.servicesPerProduct().containsKey(empty.id())
        adapter.findByServiceId(payments.services()[1].id()).get().services()*.name() == ['gateway', 'ledger', 'mobile']
        adapter.findByServiceId(9999L) == Optional.empty()
        adapter.load(9999L) == Optional.empty()
    }

    def "the directory finds products by code and name and departments by id, and services may share metrics tags and a SonarQube key"() {
        given:
        def shared = settings(sonar: SonarSettings.of(null, 'cert', command(['sonarqube'])),
                metrics: new MetricsSettings(true, 'Cert Scanner', 'test', null, null))
        def stored = adapter.save(Product.create(details(), account(), [draft('gui', null, shared),
                                                                       draft('api', null, shared)], adapter))
        entities.clear()

        expect:
        adapter.findProductByCode('cert') == Optional.of(new ProductIdentity(stored.id(), 'CertScanner'))
        adapter.findProductByName('CERTSCANNER') == Optional.of(new ProductIdentity(stored.id(), 'CertScanner'))
        adapter.findProductByCode('PAY') == Optional.empty()
        adapter.findProductByName('Payments') == Optional.empty()
        adapter.departmentExists(DEPARTMENT_ID)
        !adapter.departmentExists(9999L)
        adapter.load(stored.id()).get().services()*.settings()*.metrics()*.influxProject() == ['Cert Scanner', 'Cert Scanner']
        adapter.load(stored.id()).get().services()*.settings()*.sonar()*.projectKey() == ['cert', 'cert']
    }

    def "deleting a product removes its services and their rows"() {
        given:
        def stored = adapter.save(Product.create(details(), account(), [new ServiceDraft(null, 'gui', null,
                fullSettings('gui'))], adapter))
        entities.clear()

        when:
        adapter.delete(stored.id())
        adapter.delete(9999L)
        entities.flush()

        then:
        adapter.load(stored.id()) == Optional.empty()
        ['DSO_PRODUCT', 'DSO_SERVICE', 'DSO_SERVICE_TEST_JOB', 'DSO_UCD_APPLICATION', 'DSO_UCD_COMPONENT',
         'DSO_SERVICE_SSH_TARGET', 'DSO_SERVICE_OPENSHIFT_TARGET'].every {
            jdbc.queryForObject("SELECT COUNT(*) FROM $it" as String, Integer) == 0
        }
    }

    private static ServiceSettings tagged(String tag) {
        settings(sonar: SonarSettings.of(null, tag, command(['sonarqube'])), metrics: new MetricsSettings(true, tag, 'uat', null, null))
    }

    private static ServiceDraft draft(String name, Long id = null, ServiceSettings settings = settings()) {
        new ServiceDraft(id, name, null, settings)
    }
}
