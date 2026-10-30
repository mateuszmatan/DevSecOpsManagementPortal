package com.bbh.itss.dso.portal.domain.shared

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.shared.Text.abbreviateBytes
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes
import static com.bbh.itss.dso.portal.domain.shared.Text.clean
import static com.bbh.itss.dso.portal.domain.shared.Text.relativePath
import static com.bbh.itss.dso.portal.domain.shared.Text.relativePaths
import static com.bbh.itss.dso.portal.domain.shared.Text.trimmed

class TextSpec extends Specification {

    def "clean keeps the first occurrence of each value, trimmed keeps repeated values, both in their order"() {
        expect:
        clean(['b', 'a', ' b']) == ['b', 'a']
        trimmed([' -s ', 'settings.xml', '', null, ' ', '-gs', 'settings.xml ']) ==
                ['-s', 'settings.xml', '-gs', 'settings.xml']
        [clean(null), trimmed(null), trimmed([])] == [[], [], []]
    }

    def "a relative path loses its '.' and empty folders and its trailing slash: '#path' is #expected"() {
        expect:
        relativePath(path) == expected

        where:
        path                         || expected
        ' ./frontend/ '              || 'frontend'
        '././web//app/./ui/'         || 'web/app/ui'
        '.'                          || null
        './'                         || null
        ' '                          || null
        null                         || null
        '**/catalog/*.versions.toml' || '**/catalog/*.versions.toml'
        '../catalog/deps.toml'       || '../catalog/deps.toml'
        '/shared/deps.toml'          || '/shared/deps.toml'
    }

    def "relative paths are listed once each, in their order, without the root"() {
        expect:
        relativePaths(['./bom/pom.xml', 'bom/pom.xml/', '.', null, 'gradle//deps.toml']) ==
                ['bom/pom.xml', 'gradle/deps.toml']
        relativePaths(null) == []
    }

    def "abbreviate cuts a text to the UTF-8 bytes of an Oracle column on a character boundary"() {
        expect:
        bytes('zażółć') == 10
        abbreviateBytes(null, 5) == null
        abbreviateBytes('abcdef', 6) == 'abcdef'
        abbreviateBytes('abc  defgh', 8) == 'abc...'
        abbreviateBytes('ąąąąą', 9) == 'ąąą...'
        abbreviateBytes('ab😀cd', 7) == 'ab...'
        bytes(abbreviateBytes('é' * 300, 160)) <= 160
    }

    def "the cleaned and trimmed lists cannot be changed"() {
        when:
        Text."$method"(['a']).add('b')

        then:
        thrown(UnsupportedOperationException)

        where:
        method << ['clean', 'trimmed']
    }
}
