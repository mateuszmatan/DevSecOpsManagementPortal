package com.bbh.itss.dso.portal.domain.monitoring

import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.ABORTED
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.DISABLED
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.FAILURE
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.NOT_BUILT
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.NO_DATA
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.SUCCESS
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.UNSTABLE
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.fromTag
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.revokedKey

class RunResultSpec extends Specification {

    def "the result tag '#tag' is #result"() {
        expect:
        fromTag(tag) == result

        where:
        tag         || result
        null        || NO_DATA
        'SUCCESS'   || SUCCESS
        ' failure ' || FAILURE
        'unstable'  || UNSTABLE
        'ABORTED'   || ABORTED
        'NOT_BUILT' || NOT_BUILT
        'RUNNING'   || NO_DATA
    }

    def "a pipeline's status is its latest run unless it is disabled, and a run is known by the time it finished"() {
        given:
        def enabled = pipeline()
        def disabled = pipeline(keys: [revokedKey(reason: 'retired')])
        def failed = new PipelineRun(Instant.parse('2026-10-01T10:00:00Z'), FAILURE, null, null, null, null, null,
                null, null, null, null, null, null)

        expect:
        RunResult.of(enabled, failed) == FAILURE
        RunResult.of(enabled, null) == NO_DATA
        RunResult.of(disabled, failed) == DISABLED

        when:
        new PipelineRun(null, SUCCESS, *([null] * 11))

        then:
        thrown(NullPointerException)
    }

    def "the worst of #statuses is #worst"() {
        expect:
        RunResult.worst(statuses) == worst

        where:
        statuses                     || worst
        []                           || NO_DATA
        [SUCCESS, FAILURE, NO_DATA]  || FAILURE
        [ABORTED, UNSTABLE]          || UNSTABLE
        [SUCCESS, DISABLED]          || SUCCESS
        [DISABLED, NO_DATA]          || NO_DATA
        [DISABLED]                   || DISABLED
        [NOT_BUILT, SUCCESS]         || NOT_BUILT
    }
}
