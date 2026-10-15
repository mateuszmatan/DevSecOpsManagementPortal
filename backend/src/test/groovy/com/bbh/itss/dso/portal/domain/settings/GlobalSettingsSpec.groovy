package com.bbh.itss.dso.portal.domain.settings

import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues.bbhDefaults
import static com.bbh.itss.dso.portal.domain.settings.Scanner.DAST
import static com.bbh.itss.dso.portal.support.CatalogFixtures.copy

class GlobalSettingsSpec extends Specification {

    static final Instant UPDATED = Instant.parse('2026-10-04T12:00:00Z')
    static final GlobalSettingsValues BBH = bbhDefaults()

    def stored = new GlobalSettings(BBH, 3, UPDATED)

    def "new settings start as the BBH defaults and read the platform, deployment and Jenkins URL from the values"() {
        given:
        def withJenkins = new GlobalSettings(BBH.withPlatform(BBH.platform().withJenkinsUrl('https://jenkins.bbh.com')), 1,
                UPDATED)

        expect:
        GlobalSettings.bbhDefaults() == new GlobalSettings(BBH, 0, null)
        stored.platform() == BBH.platform()
        stored.values().deployment() == BBH.deployment()
        stored.jenkinsUrl() == null
        withJenkins.jenkinsUrl() == 'https://jenkins.bbh.com'

        when:
        new GlobalSettings(null, 0, null)

        then:
        def e = thrown(NullPointerException)
        e.message == 'values'
    }

    def "a change read at version #version takes the new values and keeps the version for the store to raise"() {
        given:
        def changed = BBH.withPlatform(BBH.platform().withJenkinsUrl('https://jenkins.bbh.com'))

        expect:
        stored.change(version, changed) == new GlobalSettings(changed, 3, UPDATED)
        stored.values() == BBH

        where:
        version << [3L, null]
    }

    def "a change read at an older version is refused"() {
        when:
        stored.change(2L, BBH)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The record was changed by someone else in the meantime. Reload it and apply your change again.'
    }

    def "a change breaking the business rules is refused with every problem"() {
        when:
        stored.change(3L, invalid)

        then:
        def e = thrown(InvalidRequestException)
        e.message == message
        e.problems() == problems

        where:
        invalid << [copy(BBH, platform: copy(BBH.platform(), proxyHost: null), limits: BBH.limits().findAll { it.key != DAST }),
                    copy(BBH, goldenFix: copy(BBH.goldenFix(), commitAuthorEmail: null))]
        message << ['2 fields are invalid', 'is required in the global settings']
        problems << [[new FieldProblem('platform.proxyHost', 'is required with a proxy port'),
                      new FieldProblem('limits.DAST', 'set the limits of every scanner')],
                     [new FieldProblem('goldenFix.commitAuthorEmail', 'is required in the global settings')]]
    }
}
