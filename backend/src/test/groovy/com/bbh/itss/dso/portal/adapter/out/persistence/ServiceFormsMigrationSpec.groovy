package com.bbh.itss.dso.portal.adapter.out.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:service-forms;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
class ServiceFormsMigrationSpec extends MigrationSpecification {

    static final List<String> PRODUCT_ACCOUNT = ['ASOC_KEY_ID', 'ASOC_SECRET_CREDENTIALS_ID']
    static final List<String> SERVICE_COLUMNS = ['APPSCAN_KEY_ID', 'APPSCAN_SCAN_NAME', 'SONAR_BADGE_CREDENTIALS_ID',
                                                 'NEXUS_DELIVERY_DISABLED']

    @Override
    protected String changelogFile() {
        'db/changelog/db.changelog-master.yaml'
    }

    def "031 gives every service its product's AppScan key, and its rollback gives each product the key of its first service"() {
        given:
        liquibase.update('')
        rollBackSince('031-')
        long cert = insert('DSO_PRODUCT', [CODE: 'CERT', NAME: 'CertScanner', ASOC_KEY_ID: 'bbh_cert',
                                           ASOC_SECRET_CREDENTIALS_ID: 'cert-secret'])
        long pay = insert('DSO_PRODUCT', [CODE: 'PAY', NAME: 'Payments Hub'])
        service(cert, 'gui', 1, null)
        service(cert, 'api', 0, 'api-secret')
        service(pay, 'gateway', 0, null)

        when:
        liquibase.update('')

        then:
        columns('DSO_PRODUCT').intersect(PRODUCT_ACCOUNT) == []
        columns('DSO_SERVICE').containsAll(SERVICE_COLUMNS)
        serviceKeys() == [api: ['bbh_cert', 'api-secret'], gateway: [null, null], gui: ['bbh_cert', 'cert-secret']]
        jdbc.queryForList('SELECT NEXUS_DELIVERY_DISABLED FROM DSO_SERVICE', Integer) == [0, 0, 0]

        when:
        jdbc.update("UPDATE DSO_SERVICE SET NEXUS_DELIVERY_DISABLED = 2 WHERE NAME = 'gui'")

        then:
        thrown(DataIntegrityViolationException)

        when:
        jdbc.update("UPDATE DSO_SERVICE SET APPSCAN_KEY_ID = 'bbh_api' WHERE NAME = 'api'")
        rollBackSince('031-')

        then:
        columns('DSO_SERVICE').intersect(SERVICE_COLUMNS) == []
        jdbc.queryForList('SELECT CODE, ASOC_KEY_ID, ASOC_SECRET_CREDENTIALS_ID FROM DSO_PRODUCT ORDER BY CODE')
                .collectEntries { [it.CODE, [it.ASOC_KEY_ID, it.ASOC_SECRET_CREDENTIALS_ID]] } ==
                [CERT: ['bbh_api', 'api-secret'], PAY: [null, null]]

        when:
        liquibase.update('')

        then:
        serviceKeys() == [api: ['bbh_api', 'api-secret'], gateway: [null, null], gui: ['bbh_api', 'cert-secret']]
    }

    private void service(long productId, String name, int displayOrder, String secretCredentialsId) {
        insert('DSO_SERVICE', [PRODUCT_ID: productId, NAME: name, DISPLAY_ORDER: displayOrder, BUILD_TOOL: 'GRADLE',
                               SOURCE_DIR: '.', DEPLOY_TARGET: 'VM', APPSCAN_APP_ID: '109f44ac-cc06-4ca0-884e-d944904f7019',
                               INFLUX_PROJECT: "CERT-$name".toString(), APPSCAN_SECRET_CREDENTIALS_ID: secretCredentialsId])
    }

    private Map<String, List<String>> serviceKeys() {
        jdbc.queryForList('SELECT NAME, APPSCAN_KEY_ID, APPSCAN_SECRET_CREDENTIALS_ID FROM DSO_SERVICE ORDER BY NAME')
                .collectEntries { [it.NAME, [it.APPSCAN_KEY_ID, it.APPSCAN_SECRET_CREDENTIALS_ID]] }
    }
}
