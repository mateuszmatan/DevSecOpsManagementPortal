package com.bbh.itss.dso.portal.domain.catalog

import groovy.json.JsonSlurper
import spock.lang.Specification

class ContextFilterSpec extends Specification {

    static final Map CASES = new JsonSlurper().parse(ContextFilterSpec.getResource('/database-rules-cases.json')) as Map

    def "the filter '#filter' deploys the test changesets: #selects"() {
        expect:
        ContextFilter.selectsTest(filter) == selects

        where:
        row << CASES.selectsTest
        filter = row.filter as String
        selects = row.selects as boolean
    }

    def "a filter that cannot be parsed, '#filter', counts as deploying the test changesets"() {
        expect:
        ContextFilter.selectsTest(filter)

        where:
        filter << [null, '  ', '(', 'rd)', '()', '(rd', '!test and (rd']
    }
}
