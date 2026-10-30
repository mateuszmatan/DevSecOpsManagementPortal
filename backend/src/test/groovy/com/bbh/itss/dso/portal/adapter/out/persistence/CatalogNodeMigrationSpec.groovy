package com.bbh.itss.dso.portal.adapter.out.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:catalog-node;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
class CatalogNodeMigrationSpec extends MigrationSpecification {

    static final List<String> SERVICE_COLUMNS = ['NODE_ENABLED', 'NODE_DIRECTORY', 'NODE_COMMANDS',
                                                 'NODE_INSTALLATION_NAME', 'NODE_PATH', 'NODE_ENVIRONMENT',
                                                 'NODE_REGISTRY', 'NODE_NPMRC_CREDENTIALS_ID', 'NODE_TIMEOUT_MINUTES',
                                                 'NODE_NEXUS_IQ_SCAN', 'GOLDEN_FIX_VERSION_CATALOGS']
    static final List<String> SETTINGS_COLUMNS = ['NODE_INSTALLATION_NAME', 'NPM_REGISTRY', 'NPMRC_CREDENTIALS_ID']

    @Override
    protected String changelogFile() {
        'db/changelog/db.changelog-master.yaml'
    }

    def "032 adds the Node.js build and the shared version catalogs to the services, switched off, and its rollback removes them"() {
        given:
        liquibase.update('')
        rollBackSince('032-')
        long product = insert('DSO_PRODUCT', [CODE: 'CERT', NAME: 'CertScanner'])
        insert('DSO_SERVICE', [PRODUCT_ID: product, NAME: 'gui', DISPLAY_ORDER: 0, BUILD_TOOL: 'GRADLE', SOURCE_DIR: '.',
                               DEPLOY_TARGET: 'VM', APPSCAN_APP_ID: '109f44ac-cc06-4ca0-884e-d944904f7019',
                               INFLUX_PROJECT: 'CERT-gui'])

        when:
        liquibase.update('')

        then:
        columns('DSO_SERVICE').containsAll(SERVICE_COLUMNS)
        columns('DSO_GLOBAL_SETTINGS').containsAll(SETTINGS_COLUMNS)
        !nullable('DSO_SERVICE', 'NODE_ENABLED')
        nullable('DSO_SERVICE', 'NODE_NEXUS_IQ_SCAN')
        jdbc.queryForMap('SELECT NODE_ENABLED, NODE_NEXUS_IQ_SCAN, GOLDEN_FIX_VERSION_CATALOGS FROM DSO_SERVICE') ==
                [NODE_ENABLED: 0, NODE_NEXUS_IQ_SCAN: null, GOLDEN_FIX_VERSION_CATALOGS: null]

        when:
        jdbc.update('UPDATE DSO_SERVICE SET NODE_ENABLED = 2')

        then:
        thrown(DataIntegrityViolationException)

        when:
        jdbc.update('UPDATE DSO_SERVICE SET NODE_NEXUS_IQ_SCAN = 2')

        then:
        thrown(DataIntegrityViolationException)

        when:
        jdbc.update("UPDATE DSO_SERVICE SET NODE_ENABLED = 1, NODE_NEXUS_IQ_SCAN = 0, NODE_COMMANDS = 'npm ci'")
        rollBackSince('032-')

        then:
        columns('DSO_SERVICE').intersect(SERVICE_COLUMNS) == []
        columns('DSO_GLOBAL_SETTINGS').intersect(SETTINGS_COLUMNS) == []
        jdbc.queryForList('SELECT NAME FROM DSO_SERVICE', String) == ['gui']

        when:
        liquibase.update('')

        then:
        columns('DSO_SERVICE').containsAll(SERVICE_COLUMNS)
        jdbc.queryForObject('SELECT NODE_ENABLED FROM DSO_SERVICE', Integer) == 0
    }
}
