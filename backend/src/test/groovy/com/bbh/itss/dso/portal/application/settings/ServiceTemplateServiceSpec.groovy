package com.bbh.itss.dso.portal.application.settings

import com.bbh.itss.dso.portal.application.settings.port.out.ServiceTemplateRepositoryPort
import com.bbh.itss.dso.portal.domain.settings.StoredServiceTemplate
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.settings.ServiceTemplate.bbhDefaults

class ServiceTemplateServiceSpec extends Specification {

    ServiceTemplateRepositoryPort repository = Mock()
    def service = new ServiceTemplateService(repository)
    def bbh = bbhDefaults()
    def stored = new StoredServiceTemplate(bbh.toBuilder().gradleTasks('build').build(), 2,
            Instant.parse('2026-10-08T12:00:00Z'))

    def "the stored template is read, and the BBH defaults stand in until one is saved"() {
        given:
        repository.load() >>> [Optional.of(stored), Optional.empty()]

        expect:
        service.current().is(stored)
        service.current() == StoredServiceTemplate.unsaved()
    }

    def "a change is checked against the stored version and saved"() {
        given:
        def changed = bbh.toBuilder().mavenTasks('verify').build()
        def saved = new StoredServiceTemplate(changed, 3, Instant.parse('2026-10-08T13:00:00Z'))

        when:
        def result = service.update(2, changed)

        then:
        1 * repository.load() >> Optional.of(stored)
        1 * repository.save(new StoredServiceTemplate(changed, 2, stored.updatedAt())) >> saved
        result.is(saved)
    }

    def "the first change saves over the defaults without a version"() {
        given:
        def changed = bbh.toBuilder().agentLabels(['docker']).build()

        when:
        service.update(null, changed)

        then:
        1 * repository.load() >> Optional.empty()
        1 * repository.save(new StoredServiceTemplate(changed, null, null))
    }

    def "a change made at an outdated version is not saved"() {
        given:
        repository.load() >> Optional.of(stored)

        when:
        service.update(1, bbh)

        then:
        thrown(IllegalStateException)
        0 * repository.save(_)
    }
}
