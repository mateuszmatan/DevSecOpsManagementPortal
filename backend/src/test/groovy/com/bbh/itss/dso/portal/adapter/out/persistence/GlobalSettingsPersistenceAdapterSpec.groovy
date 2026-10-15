package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.settings.GlobalSettings
import com.bbh.itss.dso.portal.domain.settings.SeverityLimits
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues.bbhDefaults
import static com.bbh.itss.dso.portal.domain.settings.Scanner.DAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SCA
import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.dso.portal.support.CatalogFixtures.copy
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:settings-adapter;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Import(GlobalSettingsPersistenceAdapter)
class GlobalSettingsPersistenceAdapterSpec extends Specification {

    @Subject
    @Autowired
    GlobalSettingsPersistenceAdapter adapter

    @Autowired
    JdbcTemplate jdbc

    def bbh = bbhDefaults()

    def "the defaults are stored under the fixed key and read back unchanged"() {
        expect:
        adapter.load() == Optional.empty()

        when:
        def saved = adapter.save(GlobalSettings.bbhDefaults())

        then:
        saved.values() == bbh
        saved.version() == 0
        saved.updatedAt() != null
        adapter.load().get() == saved
        adapter.load().get().values().limits().keySet() as List == [SAST, SCA, NEXUS_IQ, DAST]
        jdbc.queryForMap('SELECT ID, RELEASE_GATE_SCANNERS, GOLDEN_FIX_ECOSYSTEMS, GOLDEN_FIX_ENABLED FROM DSO_GLOBAL_SETTINGS') ==
                [ID: 1, RELEASE_GATE_SCANNERS: 'SAST,SCA,NEXUS_IQ,DAST', GOLDEN_FIX_ECOSYSTEMS: 'maven,npm,pypi',
                 GOLDEN_FIX_ENABLED: 1]
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_GLOBAL_SEVERITY_LIMIT WHERE SETTINGS_ID = 1', Integer) == 4
    }

    def "a change is written over the stored settings as the next version"() {
        given:
        def stored = adapter.save(GlobalSettings.bbhDefaults())
        def changed = copy(bbh, platform: bbh.platform().withJenkinsUrl('https://jenkins.bbh.com'),
                limits: bbh.limits() + [(SAST): new SeverityLimits(0, 2, 10)])

        when:
        def saved = adapter.save(stored.change(stored.version(), changed))

        then:
        saved.values() == changed
        saved.version() == stored.version() + 1
        adapter.load().get().jenkinsUrl() == 'https://jenkins.bbh.com'
        jdbc.queryForMap("SELECT MAX_CRITICAL, MAX_HIGH, MAX_MEDIUM FROM DSO_GLOBAL_SEVERITY_LIMIT WHERE SCANNER = 'SAST'") ==
                [MAX_CRITICAL: 0, MAX_HIGH: 2, MAX_MEDIUM: 10]
    }

    def "settings read at another version than the stored one are not written"() {
        given:
        def stored = adapter.save(GlobalSettings.bbhDefaults())
        def stale = new GlobalSettings(bbh.withPlatform(bbh.platform().withJenkinsUrl('https://stale.bbh.com')),
                stored.version() + 5, stored.updatedAt())

        when:
        adapter.save(stale)

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION
        adapter.load().get().jenkinsUrl() == null
    }
}
