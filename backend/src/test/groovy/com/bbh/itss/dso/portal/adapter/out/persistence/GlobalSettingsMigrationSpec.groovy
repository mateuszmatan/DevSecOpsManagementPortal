package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.settings.DatabaseDefaults
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import static com.bbh.itss.dso.portal.support.CatalogFixtures.copy
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:file:./build/global-settings-migration/db;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import(GlobalSettingsPersistenceAdapter)
class GlobalSettingsMigrationSpec extends MigrationSpecification {

    static final List<String> SCA = ['SCA_ENABLED', 'SCA_POLL_TIMEOUT_MIN', 'SCA_POLL_INTERVAL_SEC']
    static final String PARALLEL_SCANS = 'CK_DSO_GLOBAL_PARALLEL_SCANS'
    static final String PARALLEL_TESTS = 'CK_DSO_GLOBAL_PARALLEL_TESTS'
    static final String PLUGIN_REPOS = 'CK_DSO_GLOBAL_PLUGIN_REPOS'
    static final List<String> PLUGIN_COLUMNS = ['PLUGIN_REPOSITORIES_ENABLED', 'GRADLE_PLUGIN_REPOSITORY',
                                                'MAVEN_PLUGIN_REPOSITORY']
    static final List<String> CHECKS = ['CK_DSO_GLOBAL_BUILD_TOOL', 'CK_DSO_GLOBAL_DEPLOY_TARGET', 'CK_DSO_GLOBAL_FLAGS',
                                        PARALLEL_SCANS, PARALLEL_TESTS, PLUGIN_REPOS, 'CK_DSO_GLOBAL_SETTINGS_ROW']

    @Autowired
    GlobalSettingsPersistenceAdapter settings

    @Override
    protected String changelogFile() {
        'db/changelog/global-settings-history.yaml'
    }

    def "023 resets a renamed release gate state file and 024 drops the SCA settings in the run that upgrades the database"() {
        given:
        jdbc.execute('DROP ALL OBJECTS')
        liquibase.update('')
        settings.save(GlobalSettings.bbhDefaults())
        rollBackSince('023-')
        jdbc.update("UPDATE DSO_GLOBAL_SETTINGS SET RELEASE_GATE_STATE_FILE = 'dso-release-gate.json', SCA_ENABLED = 0")
        reopen()

        expect:
        columns('DSO_GLOBAL_SETTINGS').containsAll(SCA)

        when:
        liquibase.update('')
        def stored = settings.load().get()
        def saved = settings.save(stored.change(stored.version(), copy(stored.values(),
                scans: copy(stored.values().scans(), coverageMinLine: 70))))

        then:
        stored.values().releaseGate().stateFile() == 'release-gate.json'
        SCA.every { !(it in columns('DSO_GLOBAL_SETTINGS')) }
        saved.values().scans().coverageMinLine() == 70
        checks() == CHECKS

        when:
        jdbc.update(statement)

        then:
        thrown(DataIntegrityViolationException)

        where:
        statement << ['UPDATE DSO_GLOBAL_SETTINGS SET GOLDEN_FIX_ENABLED = 2',
                      "UPDATE DSO_GLOBAL_SETTINGS SET DEFAULT_BUILD_TOOL = 'ANT'",
                      "UPDATE DSO_GLOBAL_SETTINGS SET DEFAULT_DEPLOY_TARGET = 'MAINFRAME'"]
    }

    def "024 rolls back to the SCA settings with the values the portal used to write"() {
        given:
        jdbc.execute('DROP ALL OBJECTS')
        liquibase.update('')
        settings.save(GlobalSettings.bbhDefaults())

        when:
        rollBackSince('024-')

        then:
        jdbc.queryForMap('SELECT SCA_ENABLED, SCA_POLL_TIMEOUT_MIN, SCA_POLL_INTERVAL_SEC FROM DSO_GLOBAL_SETTINGS') ==
                [SCA_ENABLED: 1, SCA_POLL_TIMEOUT_MIN: 40, SCA_POLL_INTERVAL_SEC: 30]
        checks() == CHECKS - PARALLEL_SCANS - PARALLEL_TESTS - PLUGIN_REPOS

        when:
        jdbc.update('UPDATE DSO_GLOBAL_SETTINGS SET SCA_ENABLED = 2')

        then:
        thrown(DataIntegrityViolationException)

        when:
        liquibase.update('')

        then:
        SCA.every { !(it in columns('DSO_GLOBAL_SETTINGS')) }
        settings.load().get().values() == GlobalSettings.bbhDefaults().values()
    }

    def "033 switches the order on for saved settings, refuses other values, and its rollback removes the column"() {
        given:
        jdbc.execute('DROP ALL OBJECTS')
        liquibase.update('')
        settings.save(GlobalSettings.bbhDefaults())

        when:
        rollBackSince('033-')

        then:
        !('PARALLEL_STATIC_ANALYSIS' in columns('DSO_GLOBAL_SETTINGS'))
        checks() == CHECKS - PARALLEL_SCANS - PARALLEL_TESTS - PLUGIN_REPOS

        when:
        liquibase.update('')

        then:
        settings.load().get().values().scans().parallelStaticAnalysis()
        !nullable('DSO_GLOBAL_SETTINGS', 'PARALLEL_STATIC_ANALYSIS')
        checks() == CHECKS

        when:
        jdbc.update('UPDATE DSO_GLOBAL_SETTINGS SET PARALLEL_STATIC_ANALYSIS = 2')

        then:
        thrown(DataIntegrityViolationException)
    }

    def "042 switches the tests and DAST at the same time on for saved settings, refuses other values, and its rollback removes the column"() {
        given:
        jdbc.execute('DROP ALL OBJECTS')
        liquibase.update('')
        settings.save(GlobalSettings.bbhDefaults())

        when:
        rollBackSince('042-')

        then:
        !('PARALLEL_DYNAMIC_ANALYSIS' in columns('DSO_GLOBAL_SETTINGS'))
        checks() == CHECKS - PARALLEL_TESTS - PLUGIN_REPOS

        when:
        liquibase.update('')

        then:
        settings.load().get().values().scans().parallelDynamicAnalysis()
        !nullable('DSO_GLOBAL_SETTINGS', 'PARALLEL_DYNAMIC_ANALYSIS')
        checks() == CHECKS

        when:
        jdbc.update('UPDATE DSO_GLOBAL_SETTINGS SET PARALLEL_DYNAMIC_ANALYSIS = 2')

        then:
        thrown(DataIntegrityViolationException)
    }

    def "043 retries a step that misses a plugin with the BBH Nexus for saved settings, refuses other values, and its rollback removes the columns"() {
        given:
        jdbc.execute('DROP ALL OBJECTS')
        liquibase.update('')
        settings.save(GlobalSettings.bbhDefaults())

        when:
        rollBackSince('043-')

        then:
        PLUGIN_COLUMNS.every { !(it in columns('DSO_GLOBAL_SETTINGS')) }
        checks() == CHECKS - PLUGIN_REPOS

        when:
        liquibase.update('')
        def defaults = settings.load().get().values().serviceDefaults()

        then:
        defaults.pluginRepositoriesEnabled()
        defaults.gradlePluginRepository() == 'https://tools.bbh.com/nexus/repository/gradle-plugins/'
        defaults.mavenPluginRepository() == 'https://tools.bbh.com/nexus/repository/maven-public/'
        !nullable('DSO_GLOBAL_SETTINGS', 'PLUGIN_REPOSITORIES_ENABLED')
        nullable('DSO_GLOBAL_SETTINGS', 'GRADLE_PLUGIN_REPOSITORY')
        nullable('DSO_GLOBAL_SETTINGS', 'MAVEN_PLUGIN_REPOSITORY')
        checks() == CHECKS

        when:
        jdbc.update('UPDATE DSO_GLOBAL_SETTINGS SET PLUGIN_REPOSITORIES_ENABLED = 2')

        then:
        thrown(DataIntegrityViolationException)
    }

    def "037 adds the Library defaults (Liquibase database changes), empty for saved settings, and its rollback removes the columns"() {
        given:
        jdbc.execute('DROP ALL OBJECTS')
        liquibase.update('')
        settings.save(GlobalSettings.bbhDefaults())

        when:
        rollBackSince('037-')

        then:
        columns('DSO_GLOBAL_SETTINGS').findAll { it.startsWith('DB_') } == []
        checks() == CHECKS - PARALLEL_TESTS - PLUGIN_REPOS

        when:
        liquibase.update('')

        then:
        settings.load().get().values().database() == DatabaseDefaults.NONE
        columns('DSO_GLOBAL_SETTINGS').count { it.startsWith('DB_') } == 15
        checks() == CHECKS

        when:
        jdbc.update("UPDATE DSO_GLOBAL_SETTINGS SET DEFAULT_BUILD_TOOL = 'LIQUIBASE'")

        then:
        jdbc.queryForObject('SELECT DEFAULT_BUILD_TOOL FROM DSO_GLOBAL_SETTINGS', String) == 'LIQUIBASE'
    }

    private List<String> checks() {
        jdbc.queryForList('''SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE TABLE_NAME = 'DSO_GLOBAL_SETTINGS' AND CONSTRAINT_TYPE = 'CHECK' ''', String).sort()
    }
}
