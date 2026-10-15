package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.settings.StoredServiceTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.dso.portal.domain.settings.ServiceTemplate.bbhDefaults
import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:template-adapter;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Import(ServiceTemplatePersistenceAdapter)
class ServiceTemplatePersistenceAdapterSpec extends Specification {

    @Subject
    @Autowired
    ServiceTemplatePersistenceAdapter adapter

    @Autowired
    JdbcTemplate jdbc

    def bbh = bbhDefaults()

    def "the first save stores the template under the fixed key and reads it back unchanged"() {
        expect:
        adapter.load() == Optional.empty()

        when:
        def saved = adapter.save(StoredServiceTemplate.unsaved())

        then:
        saved.template() == bbh
        saved.version() == 0
        saved.updatedAt() != null
        adapter.load().get() == saved
        jdbc.queryForMap('SELECT ID, AGENT_LABELS, JENKINS_JOB, OPEN_SHIFT_PROJECT FROM DSO_SERVICE_TEMPLATE') ==
                [ID: 1, AGENT_LABELS: 'linux-agent', JENKINS_JOB: 'DevSecOps/{CODE}/{service}-{type}',
                 OPEN_SHIFT_PROJECT: '{code}-{service}']
    }

    def "a change is written over the stored template as the next version"() {
        given:
        def stored = adapter.save(StoredServiceTemplate.unsaved())
        def changed = bbh.toBuilder().agentLabels(['linux', 'docker']).healthCheckUrl(null).build()

        when:
        def saved = adapter.save(stored.change(stored.version(), changed))

        then:
        saved.template() == changed
        saved.version() == stored.version() + 1
        jdbc.queryForMap('SELECT AGENT_LABELS, HEALTH_CHECK_URL FROM DSO_SERVICE_TEMPLATE') ==
                [AGENT_LABELS: 'linux,docker', HEALTH_CHECK_URL: null]
    }

    def "a template read at another version than the stored one is not written"() {
        given:
        def stored = adapter.save(StoredServiceTemplate.unsaved())
        def changed = bbh.toBuilder().gradleTasks('build').build()

        when:
        adapter.save(new StoredServiceTemplate(changed, version.call(stored), null))

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION
        adapter.load().get().template() == bbh

        where:
        version << [{ it.version() + 1 }, { null }]
    }

    def "a template saved by someone else first refuses the defaults read before"() {
        when:
        adapter.save(new StoredServiceTemplate(bbh, 0, null))

        then:
        thrown(IllegalStateException)
        adapter.load() == Optional.empty()
    }
}
