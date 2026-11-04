package com.bbh.itss.dso.portal.adapter.out.persistence

import liquibase.exception.LiquibaseException
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:sql-plsql;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
class SqlPlsqlMigrationSpec extends MigrationSpecification {

    static final String TARGETS = 'DSO_SERVICE_DATABASE_TARGET'

    @Override
    protected String changelogFile() {
        'db/changelog/db.changelog-master.yaml'
    }

    def "037 adds the database settings, empty for the existing services, and its rollback waits until no service uses LIQUIBASE"() {
        given:
        liquibase.update('')
        rollBackSince('037-')
        long product = insert('DSO_PRODUCT', [CODE: 'ORD', NAME: 'Orders'])
        service(product, 'orders-app', 'GRADLE')

        when:
        liquibase.update('')

        then:
        databaseColumns() == [30, 15]
        TARGETS in tables()
        jdbc.queryForMap('SELECT NAME, DB_CHANGELOG_FILE, DB_TAG, DB_UTPLSQL FROM DSO_SERVICE') ==
                [NAME: 'orders-app', DB_CHANGELOG_FILE: null, DB_TAG: null, DB_UTPLSQL: null]

        when:
        long database = service(product, 'orders-db', 'LIQUIBASE')
        jdbc.update('UPDATE DSO_SERVICE SET DB_TAG = 0, DB_UTPLSQL = 1 WHERE ID = ?', database)
        ['TEST', 'RD', 'QC'].each { target(database, it) }

        then:
        jdbc.queryForList('SELECT DB_TAG FROM DSO_SERVICE ORDER BY NAME', Integer) == [null, 0]
        jdbc.queryForObject("SELECT COUNT(*) FROM $TARGETS" as String, Integer) == 3

        when:
        jdbc.update("UPDATE DSO_SERVICE SET BUILD_TOOL = 'ANT' WHERE ID = ?", database)

        then:
        thrown(DataIntegrityViolationException)

        when:
        jdbc.update('UPDATE DSO_SERVICE SET DB_TAG = 2 WHERE ID = ?', database)

        then:
        thrown(DataIntegrityViolationException)

        when:
        target(database, 'PROD')

        then:
        thrown(DataIntegrityViolationException)

        when:
        rollBackSince('037-')

        then:
        thrown(LiquibaseException)
        jdbc.queryForList("SELECT ID FROM DATABASECHANGELOG WHERE ID LIKE '037-%' ORDER BY ORDEREXECUTED", String) ==
                ['037-service-database', '037-service-database-flags', '037-service-database-target',
                 '037-global-database', '037-service-build-tool-old']
        databaseColumns() == [30, 15]
        jdbc.queryForObject("SELECT COUNT(*) FROM $TARGETS" as String, Integer) == 3

        when:
        jdbc.update("UPDATE DSO_SERVICE SET BUILD_TOOL = 'GRADLE' WHERE ID = ?", database)
        rollBackSince('037-')

        then:
        databaseColumns() == [0, 0]
        !(TARGETS in tables())
        jdbc.queryForList('SELECT NAME FROM DSO_SERVICE ORDER BY NAME', String) == ['orders-app', 'orders-db']

        when:
        liquibase.update('')
        long again = service(product, 'orders-schema', 'LIQUIBASE')
        target(again, 'TEST')
        jdbc.update('DELETE FROM DSO_SERVICE WHERE ID = ?', again)

        then:
        databaseColumns() == [30, 15]
        jdbc.queryForObject("SELECT COUNT(*) FROM $TARGETS" as String, Integer) == 0
    }

    private long service(long productId, String name, String buildTool) {
        insert('DSO_SERVICE', [PRODUCT_ID: productId, NAME: name, DISPLAY_ORDER: 0, BUILD_TOOL: buildTool, SOURCE_DIR: '.',
                               DEPLOY_TARGET: 'VM', APPSCAN_APP_ID: '109f44ac-cc06-4ca0-884e-d944904f7019',
                               INFLUX_PROJECT: "ORD-$name".toString()])
    }

    private void target(long serviceId, String target) {
        jdbc.update("INSERT INTO $TARGETS (SERVICE_ID, TARGET) VALUES (?, ?)" as String, serviceId, target)
    }

    private List<Integer> databaseColumns() {
        ['DSO_SERVICE', 'DSO_GLOBAL_SETTINGS'].collect { table -> columns(table).count { it.startsWith('DB_') } }
    }
}
