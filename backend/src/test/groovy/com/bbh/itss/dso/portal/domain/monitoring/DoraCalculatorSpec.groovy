package com.bbh.itss.dso.portal.domain.monitoring

import com.bbh.itss.dso.portal.domain.monitoring.DoraSummary.DailyActivity
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static com.bbh.itss.dso.portal.domain.monitoring.DoraCalculator.summarize
import static com.bbh.itss.dso.portal.domain.monitoring.DoraCalculator.summarizeAll
import static com.bbh.itss.dso.portal.domain.monitoring.DoraLevel.ELITE
import static com.bbh.itss.dso.portal.domain.monitoring.DoraLevel.HIGH
import static com.bbh.itss.dso.portal.domain.monitoring.DoraLevel.LOW
import static com.bbh.itss.dso.portal.domain.monitoring.DoraLevel.MEDIUM
import static java.lang.Math.abs

class DoraCalculatorSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-04T12:00:00Z')

    def "no runs give no metrics, only empty days"() {
        given:
        def days = (0..6).collect { new DailyActivity(LocalDate.parse('2026-09-28').plusDays(it), 0, 0, 0) }

        expect:
        summarize([], 7, NOW) == DoraSummary.builder().rangeDays(7).deploymentsPerWeek(0.0d).daily(days).build()
    }

    def "a month of runs in any order gives the four metrics over its deployments"() {
        given:
        def points = [
                point('2026-10-03T08:30:00Z', true, false, 5400, 600),
                point('2026-10-01T10:00:00Z', true, false, 3600, 600),
                point('2026-10-02T09:00:00Z', false, true, 7200, 900),
                point('2026-10-04T10:00:00Z', true, true, 3600, 300),
                point('2026-10-02T11:00:00Z', true, true, 0, 300),
                point('2026-10-02T13:00:00Z', true, false, 10800, 600),
                point('2026-10-03T08:00:00Z', false, true, 1800, 600)]

        when:
        def summary = summarize(points, 30, NOW)

        then: 'five of the seven runs deployed'
        summary.runs() == 7
        summary.deployments() == 5
        abs(summary.deploymentsPerWeek() - 1.1666d) < 0.0001
        summary.deploymentFrequencyLevel() == HIGH

        and: 'the median of the positive lead times of the deployments, not of the builds between them'
        summary.leadTimeMedianSeconds() == 4500
        summary.leadTimeLevel() == ELITE

        and: 'two of five deployments failed; the builds that never deployed do not count'
        summary.changeFailureRatePercent() == 40.0d
        summary.changeFailureRateLevel() == LOW

        and: 'restored two hours after the failed deployment, failing again since the last deployment'
        summary.restores() == 1
        summary.meanTimeToRestoreSeconds() == 7200
        summary.timeToRestoreLevel() == HIGH
        summary.failingSince() == Instant.parse('2026-10-04T10:00:00Z')

        and: 'the average duration of every run'
        summary.averageDurationSeconds() == 557

        and: 'the activity of each day'
        summary.daily().size() == 30
        summary.daily().last().date() == LocalDate.parse('2026-10-04')
        day(summary, '2026-10-02') == [3, 2, 2]
        day(summary, '2026-10-04') == [1, 1, 1]
        day(summary, '2026-09-30') == [0, 0, 0]
    }

    def "runs that never deployed give no lead time, no change failure rate and no time to restore"() {
        given:
        def points = [point('2026-10-01T10:00:00Z', false, false, 3600, 600),
                      point('2026-10-02T10:00:00Z', false, true, 7200, 600),
                      point('2026-10-03T10:00:00Z', false, false, 1800, 600)]

        when:
        def summary = summarize(points, 30, NOW)

        then:
        summary.with { [runs(), deployments(), restores(), averageDurationSeconds()] } == [3, 0, 0, 600L]
        summary.with { [leadTimeMedianSeconds(), leadTimeLevel(), changeFailureRatePercent(), changeFailureRateLevel(),
                        meanTimeToRestoreSeconds(), timeToRestoreLevel(), failingSince()] }.every { it == null }
        day(summary, '2026-10-02') == [1, 1, 0]
    }

    def "a failed build between a failed deployment and the next one does not end the outage"() {
        given:
        def points = [point('2026-10-01T10:00:00Z', true, true, 60, 60),
                      point('2026-10-01T11:00:00Z', false, false, 60, 60),
                      point('2026-10-01T12:00:00Z', false, true, 60, 60),
                      point('2026-10-01T13:00:00Z', true, false, 60, 60)]

        when:
        def summary = summarize(points, 30, NOW)

        then:
        summary.restores() == 1
        summary.meanTimeToRestoreSeconds() == 10_800
        summary.failingSince() == null
        summary.changeFailureRatePercent() == 50.0d
    }

    def "the runs of several pipelines add up, and an outage ends only with a deployment of the same pipeline"() {
        given:
        def gui = [point('2026-10-01T10:00:00Z', true, true, 600, 60),
                   point('2026-10-01T14:00:00Z', true, false, 1200, 60)]
        def api = [point('2026-10-01T12:00:00Z', true, false, 1800, 120),
                   point('2026-10-02T09:00:00Z', true, true, 2400, 120)]

        when:
        def summary = summarizeAll([gui, api], 7, NOW)

        then:
        summary.runs() == 4
        summary.deployments() == 4
        summary.changeFailureRatePercent() == 50.0d
        summary.restores() == 1
        summary.meanTimeToRestoreSeconds() == 14_400
        summary.failingSince() == Instant.parse('2026-10-02T09:00:00Z')
        summary.leadTimeMedianSeconds() == 1500
        summary.averageDurationSeconds() == 90
        day(summary, '2026-10-01') == [3, 1, 3]
        summarizeAll([], 7, NOW).runs() == 0
    }

    def "runs before the first day of the chart count in no total, a range without days has no rate, and an odd count has the middle median"() {
        given:
        def old = summarize([point('2026-08-01T10:00:00Z', true, false, 60, 60),
                             point('2026-09-27T13:00:00Z', true, true, 60, 60),
                             point('2026-09-28T00:00:00Z', true, false, 120, 60)], 7, NOW)
        def none = summarize([point('2026-10-04T10:00:00Z', true, false, 60, 60)], 0, NOW)
        def odd = summarize([point('2026-10-01T10:00:00Z', true, false, 100, 1),
                             point('2026-10-02T10:00:00Z', true, false, 900, 1),
                             point('2026-10-03T10:00:00Z', true, false, 300, 1)], 30, NOW)

        expect:
        old.with { [runs(), deployments(), restores(), changeFailureRatePercent(), leadTimeMedianSeconds()] } ==
                [1, 1, 0, 0.0d, 120L]
        old.daily().sum { it.runs() } == 1
        day(old, '2026-09-28') == [1, 0, 1]
        none.with { [deploymentsPerWeek(), deploymentFrequencyLevel(), daily()] } == [null, null, []]
        odd.leadTimeMedianSeconds() == 300
    }

    def "#perWeek deployments a week rate #level"() {
        expect:
        DoraCalculator.deploymentFrequencyLevel(perWeek) == level

        where:
        perWeek  || level
        7.0d     || ELITE
        6.99d    || HIGH
        1.0d     || HIGH
        0.99d    || MEDIUM
        7.0d / 30 || MEDIUM
        0.2d     || LOW
    }

    def "a lead time of #seconds s rates #level"() {
        expect:
        DoraCalculator.leadTimeLevel(seconds) == level

        where:
        seconds   || level
        86_399    || ELITE
        86_400    || HIGH
        604_799   || HIGH
        604_800   || MEDIUM
        2_591_999 || MEDIUM
        2_592_000 || LOW
    }

    def "a time to restore of #seconds s rates #level"() {
        expect:
        DoraCalculator.timeToRestoreLevel(seconds) == level

        where:
        seconds || level
        3_599   || ELITE
        3_600   || HIGH
        86_400  || MEDIUM
        604_800 || LOW
    }

    def "a change failure rate of #percent % rates #level"() {
        expect:
        DoraCalculator.changeFailureRateLevel(percent) == level

        where:
        percent || level
        0.0d    || ELITE
        5.0d    || ELITE
        5.01d   || HIGH
        10.0d   || HIGH
        15.0d   || MEDIUM
        15.1d   || LOW
    }

    private static DoraPoint point(String time, boolean deployment, boolean failure, long lead, long duration) {
        new DoraPoint(Instant.parse(time), deployment, failure, lead, duration)
    }

    private static List<Integer> day(DoraSummary summary, String date) {
        def day = summary.daily().find { it.date() == LocalDate.parse(date) }
        [day.runs(), day.failures(), day.deployments()]
    }
}
