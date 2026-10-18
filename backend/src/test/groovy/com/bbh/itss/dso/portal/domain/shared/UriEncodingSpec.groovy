package com.bbh.itss.dso.portal.domain.shared

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.shared.UriEncoding.pathSegment
import static java.nio.charset.StandardCharsets.UTF_8
import static org.springframework.web.util.UriUtils.encodePathSegment

class UriEncodingSpec extends Specification {

    static final String EVERY_SYMBOL = (32..126).collect { (char) it }.join('') + 'é€\u007f'

    def "a path segment keeps the characters RFC 3986 allows in it and encodes the rest in upper case hex"() {
        expect:
        pathSegment(segment) == encoded

        where:
        segment                       || encoded
        'AZaz09-._~'                  || 'AZaz09-._~'
        "!\$&'()*+,;=:@"              || "!\$&'()*+,;=:@"
        'a b/c?d#e%f[g]h"i<j>k\\l^m`' || 'a%20b%2Fc%3Fd%23e%25f%5Bg%5Dh%22i%3Cj%3Ek%5Cl%5Em%60'
        '{|}'                         || '%7B%7C%7D'
        'é€'                          || '%C3%A9%E2%82%AC'
        '\u007f'                      || '%7F'
    }

    def "the encoding matches the one Spring applies to path segments"() {
        expect:
        pathSegment(EVERY_SYMBOL) == encodePathSegment(EVERY_SYMBOL, UTF_8)
    }
}
