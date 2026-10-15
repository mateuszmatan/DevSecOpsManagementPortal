package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.adapter.RecordMapper
import com.bbh.itss.dso.portal.adapter.out.persistence.GlobalSettingsEntity.SeverityLimitsEmbeddable
import com.bbh.itss.dso.portal.adapter.out.persistence.ServiceEntity.GoldenFixPolicyEmbeddable
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings
import com.bbh.itss.dso.portal.domain.settings.SeverityLimits
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.adapter.out.persistence.GlobalSettingsEntity.ID
import static com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy.INHERITED
import static com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues.bbhDefaults
import static com.bbh.itss.dso.portal.domain.settings.Scanner.DAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SCA
import static com.bbh.itss.dso.portal.support.CatalogFixtures.copy
import static org.springframework.test.util.ReflectionTestUtils.getField
import static org.springframework.test.util.ReflectionTestUtils.setField

class GlobalSettingsEntitySpec extends Specification {

    static final Instant UPDATED = Instant.parse('2026-10-04T12:00:00Z')

    def entity = new GlobalSettingsEntity()
    def bbh = bbhDefaults()

    def "the settings live under one fixed key and every section is read back unchanged with the stored version"() {
        given:
        setField(entity, 'version', 7L)
        setField(entity, 'updatedAt', UPDATED)

        when:
        entity.apply(bbh)

        then:
        entity.id == ID
        entity.isNew()
        entity.toDomain() == new GlobalSettings(bbh, 7, UPDATED)
        entity.toDomain().values().limits().keySet() as List == [SAST, SCA, NEXUS_IQ, DAST]
        getField(entity, 'values').releaseGate().scanners() == [SAST, SCA, NEXUS_IQ, DAST]

        when:
        setField(entity, 'createdAt', UPDATED)

        then:
        !entity.isNew()
    }

    def "unchanged limits are left alone so saving writes no limit rows"() {
        given:
        entity.apply(bbh)
        Map stored = Spy(HashMap, constructorArgs: [getField(entity, 'limits')])
        setField(entity, 'limits', stored)

        when:
        entity.apply(bbh)

        then:
        0 * stored.clear()
        0 * stored.putAll(_)

        when:
        entity.apply(copy(bbh, limits: [(SAST): new SeverityLimits(3, 2, 1)]))

        then:
        1 * stored.clear()
        1 * stored.putAll({ it == [(SAST): new SeverityLimitsEmbeddable(3, 2, 1)] })
    }

    def "a GoldenFix policy keeps every value through its columns"() {
        expect:
        RecordMapper.map(RecordMapper.map(policy, GoldenFixPolicyEmbeddable), GoldenFixPolicy) == policy

        where:
        policy << [INHERITED,
                   new GoldenFixPolicy(false, true, 8, ['maven'], ['recommended-non-breaking'], ['legacy'], true, 2, 30,
                           'mvn verify', 'gradle check', 'npm test', 'pytest', 'flutter test', 'Bot', 'bot@bbh.com', 'UTC')]
    }
}
