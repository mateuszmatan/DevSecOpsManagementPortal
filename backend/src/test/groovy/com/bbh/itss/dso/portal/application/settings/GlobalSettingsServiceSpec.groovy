package com.bbh.itss.dso.portal.application.settings

import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues.bbhDefaults
import static java.time.Instant.EPOCH

class GlobalSettingsServiceSpec extends Specification {

    GlobalSettingsRepositoryPort repository = Mock()
    def service = new GlobalSettingsService(repository)
    def bbh = bbhDefaults()
    def stored = new GlobalSettings(bbh, 3, Instant.parse('2026-10-04T12:00:00Z'))

    def "the BBH defaults are created only when the database has no settings"() {
        when:
        def result = service.ensureExists()

        then:
        1 * repository.load() >> Optional.ofNullable(existing)
        (existing ? 0 : 1) * repository.save(GlobalSettings.bbhDefaults()) >> stored
        result.is(existing ?: stored)

        where:
        existing << [null, new GlobalSettings(bbhDefaults(), 3, EPOCH)]
    }

    def "the current settings are read from the store, and missing ones are an error of the start-up"() {
        given:
        repository.load() >>> [Optional.of(stored), Optional.empty()]

        expect:
        service.current().is(stored)

        when:
        service.current()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'The global settings are missing; the portal creates them at start-up'
    }

    def "a change is checked against the stored settings and saved"() {
        given:
        def changed = bbh.withPlatform(bbh.platform().withJenkinsUrl('https://jenkins.bbh.com'))
        def saved = new GlobalSettings(changed, 4, EPOCH)

        when:
        def result = service.update(version, changed)

        then:
        1 * repository.load() >> Optional.of(stored)

        then:
        1 * repository.save(new GlobalSettings(changed, 3, stored.updatedAt())) >> saved

        result.is(saved)

        where:
        version << [3L, null]
    }

    def "a refused change is not saved"() {
        given:
        repository.load() >> Optional.of(stored)

        when:
        service.update(2L, bbh)

        then:
        thrown(IllegalStateException)
        0 * repository.save(_)
    }
}
