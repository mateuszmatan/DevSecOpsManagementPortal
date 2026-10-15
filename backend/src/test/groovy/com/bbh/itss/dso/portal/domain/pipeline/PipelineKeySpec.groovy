package com.bbh.itss.dso.portal.domain.pipeline

import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.pipeline.KeyStatus.ACTIVE
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineKey.normalize

class PipelineKeySpec extends Specification {

    static final Instant ISSUED = Instant.parse('2026-10-01T08:00:00Z')
    static final Instant REVOKED = Instant.parse('2026-10-02T08:00:00Z')

    def "a key trims its revocation reason and is shown by its first eight and last four characters"() {
        expect:
        new PipelineKey(1L, '0f8fad5b-d9cb-469f-a165-70867728950e', ACTIVE, ISSUED, null, null, null).hint() ==
                '0f8fad5b\u2026950e'
        new PipelineKey(1L, 'k', KeyStatus.REVOKED, ISSUED, REVOKED, '  retired  ', null).revokeReason() == 'retired'
        new PipelineKey(1L, 'k', KeyStatus.REVOKED, ISSUED, REVOKED, null, null).revokeReason() == null
    }

    def "a key carries a revocation time exactly when it is revoked: #status, #revokedAt"() {
        when:
        new PipelineKey(1L, 'k', status, ISSUED, revokedAt, null, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'a key carries a revocation time exactly when it is revoked'

        where:
        status            | revokedAt
        ACTIVE            | REVOKED
        KeyStatus.REVOKED | null
    }

    def "a key needs its #missing"() {
        when:
        factory()

        then:
        def e = thrown(NullPointerException)
        e.message == "a key needs $missing"

        where:
        missing                     | factory
        'its value'                 | { new PipelineKey(1L, null, ACTIVE, ISSUED, null, null, null) }
        'its status'                | { new PipelineKey(1L, 'k', null, ISSUED, null, null, null) }
        'the time it was issued'    | { new PipelineKey(1L, 'k', ACTIVE, null, null, null, null) }
    }

    def "a key value given by a pipeline is compared trimmed and in lower case: '#value'"() {
        expect:
        normalize(value) == normalized

        where:
        value                                     || normalized
        ' 0F8FAD5B-D9CB-469F-A165-70867728950E\t' || '0f8fad5b-d9cb-469f-a165-70867728950e'
        '0f8fad5b'                                || '0f8fad5b'
        '  '                                      || ''
        null                                      || ''
    }

    def "only an active key passes the check for an active key"() {
        given:
        def active = new PipelineKey(1L, 'k', ACTIVE, ISSUED, null, null, null)
        def revoked = new PipelineKey(2L, 'r', KeyStatus.REVOKED, ISSUED, REVOKED, 'leaked', null)

        when:
        active.requireActive()

        then:
        noExceptionThrown()

        when:
        revoked.requireActive()

        then:
        def e = thrown(SecurityException)
        e.message == "The DevSecOps pipeline key was invalidated on $REVOKED: leaked"
    }
}
