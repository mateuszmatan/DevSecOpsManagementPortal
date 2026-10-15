package com.bbh.itss.dso.portal.domain.pipeline

import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.Fixtures.activeKey
import static com.bbh.itss.dso.portal.support.Fixtures.revokedKey

class IssuedKeySpec extends Specification {

    def "an active key authorizes the pipeline it was issued for"() {
        expect:
        new IssuedKey(20L, activeKey()).authorize() == 20L
    }

    def "a revoked key is refused with the time and the reason it was invalidated"() {
        given:
        def revoked = revokedKey(reason: 'Service retired')

        when:
        new IssuedKey(20L, revoked).authorize()

        then:
        def e = thrown(SecurityException)
        e.message == "The DevSecOps pipeline key was invalidated on ${revoked.revokedAt()}: Service retired"
    }

    def "an issued key names the key"() {
        when:
        new IssuedKey(20L, null)

        then:
        def e = thrown(NullPointerException)
        e.message == 'an issued key names the key'
    }
}
