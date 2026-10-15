package com.bbh.itss.dso.portal.domain.monitoring

import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.monitoring.MetricsRow.doraPoint
import static com.bbh.itss.dso.portal.domain.monitoring.MetricsRow.tag
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.NO_DATA
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.UNSTABLE

class MetricsRowSpec extends Specification {

    def "a run is read from a pivoted pipeline_run row, missing text as null, and its tag from project and environment"() {
        when:
        def run = MetricsRow.run([_time       : '2026-10-01T10:00:00Z', result: 'unstable', branch: 'develop',
                                  build       : '42', duration_s: '1260.0', commit: 'a1b2c3', job: 'CERT/gui',
                                  stages_total: '12', passed: '11', warned: '1', failed: '0', blocked: '0',
                                  skipped     : ''])

        then:
        run == new PipelineRun(Instant.parse('2026-10-01T10:00:00Z'), UNSTABLE, 'develop', 42, 1260,
                'a1b2c3', 'CERT/gui', 12, 11, 1, 0, 0, null)
        MetricsRow.run([_time: '2026-10-01T10:00:00Z', branch: ' ']) == new PipelineRun(
                Instant.parse('2026-10-01T10:00:00Z'), NO_DATA, *([null] * 11))
        tag([project: 'CERT-gui', env: 'test', _time: '2026-10-01T10:00:00Z']) == new MetricsTag('CERT-gui', 'test')
    }

    def "a DORA point is read from a pivoted dora row, a row without a time is none"() {
        expect:
        doraPoint([_time: '2026-10-01T10:00:00Z', deployment: '1.0', change_failure: '0', lead_time_s: '3600',
                   duration_s: '600.0']) ==
                new DoraPoint(Instant.parse('2026-10-01T10:00:00Z'), true, false, 3600, 600)
        doraPoint([_time: '2026-10-02T10:00:00Z', change_failure: 'yes']) ==
                new DoraPoint(Instant.parse('2026-10-02T10:00:00Z'), false, false, 0, 0)
        doraPoint([deployment: '1']) == null
    }

    def "the value '#value' reads as the number #number and the decimal #decimal"() {
        expect:
        MetricsRow.number(value) == number
        MetricsRow.decimal(value) == decimal

        where:
        value  || number | decimal
        null   || null   | null
        ''     || null   | null
        '   '  || null   | null
        'abc'  || null   | null
        '12'   || 12L    | 12.0d
        ' 7 '  || 7L     | 7.0d
        '12.9' || 12L    | 12.9d
        '-1.5' || -2L    | -1.5d
        '1e3'  || 1000L  | 1000.0d
    }
}
