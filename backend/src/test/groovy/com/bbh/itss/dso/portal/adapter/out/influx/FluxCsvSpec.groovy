package com.bbh.itss.dso.portal.adapter.out.influx

import spock.lang.Specification

import static com.bbh.itss.dso.portal.adapter.out.influx.FluxCsv.splitLine

class FluxCsvSpec extends Specification {

    def "rows are read by column name without the yield column"() {
        given:
        def csv = ',result,table,_time,project,result,build\r\n' +
                ',_result,0,2026-10-01T10:00:00Z,CERT-gui,SUCCESS,12\r\n' +
                ',_result,1,2026-10-02T10:00:00Z,CERT-api,FAILURE,3\r\n'

        expect:
        FluxCsv.parse(csv) == [
                [table: '0', _time: '2026-10-01T10:00:00Z', project: 'CERT-gui', result: 'SUCCESS', build: '12'],
                [table: '1', _time: '2026-10-02T10:00:00Z', project: 'CERT-api', result: 'FAILURE', build: '3']]
    }

    def "tables of a different shape start with their own header after an empty line"() {
        given:
        def csv = ',result,table,project,build\n,_result,0,CERT-gui,12\n\n,result,table,project,branch\n,_result,1,CERT-api,main\n'

        expect:
        FluxCsv.parse(csv) == [[table: '0', project: 'CERT-gui', build: '12'], [table: '1', project: 'CERT-api', branch: 'main']]
    }

    def "quoted cells may hold commas and quotes"() {
        expect:
        splitLine('a,"b, c","say ""hi""",') == ['a', 'b, c', 'say "hi"', '']
    }

    def "#description"() {
        expect:
        FluxCsv.parse(csv) == rows

        where:
        description                                      | csv                                                 || rows
        'no CSV means no rows'                           | null                                                || []
        'an empty CSV means no rows'                     | ''                                                  || []
        'a CSV of an empty line means no rows'           | '\r\n'                                              || []
        'a repeated header is not a row'                 | 'project,build\nCERT-gui,1\nproject,build\nCERT-api,2' || [[project: 'CERT-gui', build: '1'], [project: 'CERT-api', build: '2']]
        'columns without a name and extra cells are dropped' | ',project,build\n,CERT-gui,1,extra\n,CERT-api'     || [[project: 'CERT-gui', build: '1'], [project: 'CERT-api']]
    }
}
