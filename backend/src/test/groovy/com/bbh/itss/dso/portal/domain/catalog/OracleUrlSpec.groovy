package com.bbh.itss.dso.portal.domain.catalog

import groovy.json.JsonSlurper
import spock.lang.Specification

class OracleUrlSpec extends Specification {

    static final Map CASES = new JsonSlurper().parse(OracleUrlSpec.getResource('/database-rules-cases.json')) as Map

    def "#row.url names the hosts #row.hosts and the service #row.service, or has the problem #row.problem"() {
        expect:
        OracleUrl.problemOf(row.url as String) == Optional.ofNullable(row.problem as OracleUrl.Problem)
        OracleUrl.parse(row.url as String) == (row.problem ? Optional.empty()
                : Optional.of(new OracleUrl(row.hosts as List<String>, row.service as String)))

        where:
        row << CASES.urls
    }

    def "#row.url is allowed by #row.entries: #row.allowed"() {
        expect:
        OracleUrl.parse(row.url as String).orElseThrow().allowedBy(row.entries as List<String>) == row.allowed

        where:
        row << CASES.allowed
    }

    def "no URL has no problem and no hosts"() {
        expect:
        OracleUrl.problemOf(null).empty
        OracleUrl.parse(null).empty
    }

    def "an entry matches in any case, its dots only as dots, and a service of the descriptor's SID"() {
        given:
        def url = OracleUrl.parse('jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(HOST=RD-DB.bbh.com)(PORT=1521))'
                + '(CONNECT_DATA=(SID=ORCL)))').orElseThrow()

        expect:
        url == new OracleUrl(['rd-db.bbh.com'], 'orcl')
        url.allowedBy(['rd-db.BBH.com/ORCL'])
        !url.allowedBy(['rd-db.bbh.com/ORDERS'])
        !url.allowedBy(['rd-dbxbbh.com'])
        !OracleUrl.parse('jdbc:oracle:thin:@//rd-dbxbbh.com:1521/X').orElseThrow().allowedBy(['rd-db.bbh.com'])
    }

    def "a port outside 1 to 65535 and an unbalanced descriptor are not a form the library deploys to"() {
        expect:
        OracleUrl.problemOf(url) == Optional.of(OracleUrl.Problem.FORM)

        where:
        url << ['jdbc:oracle:thin:@//rd-db.bbh.com:0/X', 'jdbc:oracle:thin:@rd-db.bbh.com:65536:ORCL',
                'jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(HOST=a.bbh.com))', 'jdbc:oracle:thin:@(DESCRIPTION=(PORT=1))']
    }
}
