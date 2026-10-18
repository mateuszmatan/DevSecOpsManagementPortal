package com.bbh.itss.dso.portal.adapter.out.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.transaction.annotation.Transactional

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:dso-tables;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
class DsoTablesMigrationSpec extends MigrationSpecification {

    static final List<String> GONE = ['DSO_CHANGE_PROFILE', 'DSO_CHANGE_PROFILE_PRIVILEGED_USER', 'DSO_CHANGE_PROFILE_TASK',
                                      'DSO_METRIC_POINT', 'DSO_PRODUCTION_CHANGE', 'DSO_PRODUCTION_CHANGE_PRIVILEGED_USER',
                                      'DSO_PRODUCTION_CHANGE_STAGE', 'DSO_PRODUCTION_CHANGE_TASK']
    static final List<String> SCAN_NAMES = ['SAST_SCAN_NAME', 'DAST_SCAN_NAME', 'SCA_SCAN_NAME']

    @Override
    protected String changelogFile() {
        'db/changelog/db.changelog-master.yaml'
    }

    def "the portal's changelog keeps the catalogue, pipeline and settings tables and drops the Beadle tables"() {
        when:
        liquibase.update('')

        then:
        tables().containsAll(['DSO_DEPARTMENT', 'DSO_GLOBAL_SETTINGS', 'DSO_PIPELINE', 'DSO_PIPELINE_KEY', 'DSO_PRODUCT',
                              'DSO_SERVICE', 'DSO_SERVICE_TEMPLATE'])
        tables().intersect(GONE) == []
        jdbc.queryForList('SELECT NAME FROM DSO_DEPARTMENT ORDER BY ID', String) ==
                ['AI Lab', 'Capital Partners', 'Corporate Technology', 'Custody', 'Fund Services']
    }

    def "030 drops the scan names the DevSecOps library never reads, and its rollback adds them back"() {
        when:
        liquibase.update('')

        then:
        columns('DSO_SERVICE').intersect(SCAN_NAMES) == []

        when:
        rollBackSince('030-')

        then:
        columns('DSO_SERVICE').containsAll(SCAN_NAMES)

        when:
        liquibase.update('')

        then:
        columns('DSO_SERVICE').intersect(SCAN_NAMES) == []
    }
}
