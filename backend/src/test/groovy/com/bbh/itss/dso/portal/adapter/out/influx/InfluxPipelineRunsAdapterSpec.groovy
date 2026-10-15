package com.bbh.itss.dso.portal.adapter.out.influx

import com.bbh.itss.dso.portal.domain.monitoring.DoraPoint
import com.bbh.itss.dso.portal.domain.monitoring.LatestRuns
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.FAILURE
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.SUCCESS
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.UNSTABLE

class InfluxPipelineRunsAdapterSpec extends Specification {

    InfluxQueryClient influx = Spy(constructorArgs: [
            new InfluxProperties('http://influx', 'DevSecOps', 'DORA-metrics', 't', '180d'), RestClient.builder()])

    def adapter = new InfluxPipelineRunsAdapter(influx)

    def gui = new MetricsTag('CERT-gui', 'test')
    def guiSast = new MetricsTag('CERT-guisast', 'test')

    def "it is configured when the client is, and a ping queries the buckets or gives the reason it failed"() {
        when:
        adapter.ping()

        then:
        adapter.configured()
        !new InfluxPipelineRunsAdapter(unconfigured()).configured()
        1 * influx.query('buckets() |> limit(n: 1)') >> []

        when:
        adapter.ping()

        then:
        1 * influx.query(_) >> { throw new IllegalStateException('Connection refused') }
        def e = thrown(UncheckedIOException)
        e.message == 'InfluxDB could not be read: Connection refused'
    }

    def "the latest run of each pipeline is read in one query, and no pipelines need none"() {
        when:
        def runs = adapter.latestRuns([gui, guiSast, new MetricsTag('CERT-gui', 'uat')] as LinkedHashSet, [] as Set)

        then:
        adapter.latestRuns([], [] as Set) == LatestRuns.none()
        1 * influx.query('''\
            from(bucket: "DORA-metrics")
              |> range(start: -180d)
              |> filter(fn: (r) => r._measurement == "pipeline_run")
              |> filter(fn: (r) => contains(value: r.project, set: ["CERT-gui", "CERT-guisast"]))
              |> last()
              |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
              |> group(columns: ["project", "env"])
              |> sort(columns: ["_time"])
              |> last(column: "_time")
            '''.stripIndent()) >> [run('2026-10-01T10:00:00Z', 'CERT-gui', 'test', 'SUCCESS'),
                                   run('2026-10-02T10:00:00Z', 'CERT-guisast', 'test', 'FAILURE'),
                                   run('2026-10-03T10:00:00Z', 'CERT-gui', 'prod', 'SUCCESS')]
        runs.runs().keySet() == [gui, guiSast] as Set
        runs.runs()[guiSast]*.result() == [FAILURE]
        runs.runs()[gui]*.time() == [Instant.parse('2026-10-01T10:00:00Z')]
    }

    def "the latest run of each Jenkins job is read for a tag several services share"() {
        given:
        def certScanner = new MetricsTag('CertScanner', 'test')

        when:
        def runs = adapter.latestRuns([gui, certScanner] as LinkedHashSet, [certScanner] as Set)

        then:
        1 * influx.query({ String flux -> flux.contains('set: ["CERT-gui"]') && flux.contains('|> last()\n') }) >>
                [run('2026-10-01T10:00:00Z', 'CERT-gui', 'test', 'SUCCESS')]
        1 * influx.query('''\
            from(bucket: "DORA-metrics")
              |> range(start: -180d)
              |> filter(fn: (r) => r._measurement == "pipeline_run")
              |> filter(fn: (r) => contains(value: r.project, set: ["CertScanner"]))
              |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
              |> group(columns: ["project", "env", "job"])
              |> sort(columns: ["_time"])
              |> last(column: "_time")
            '''.stripIndent()) >> [run('2026-10-02T10:00:00Z', 'CertScanner', 'test', 'SUCCESS') + [job: 'CertScanner-gui'],
                                   run('2026-10-03T10:00:00Z', 'CertScanner', 'test', 'FAILURE') + [job: 'CertScanner-api']]
        runs.runs()[gui]*.result() == [SUCCESS]
        runs.runs()[certScanner]*.job() == ['CertScanner-gui', 'CertScanner-api']
        runs.sharedTags() == [certScanner] as Set
    }

    def "recent runs are read newest first for one pipeline"() {
        when:
        def runs = adapter.recentRuns(gui, null, 30, 25)

        then:
        1 * influx.query('''\
            from(bucket: "DORA-metrics")
              |> range(start: -30d)
              |> filter(fn: (r) => r._measurement == "pipeline_run")
              |> filter(fn: (r) => r.project == "CERT-gui" and r.env == "test")
              |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
              |> group()
              |> sort(columns: ["_time"], desc: true)
              |> limit(n: 25)
            '''.stripIndent()) >> [run('2026-10-02T10:00:00Z', 'CERT-gui', 'test', 'UNSTABLE'),
                                   run('2026-10-01T10:00:00Z', 'CERT-gui', 'test', 'SUCCESS')]
        runs*.result() == [UNSTABLE, SUCCESS]
    }

    def "recent runs of a tag several services share are those of one Jenkins job or its branches"() {
        when:
        adapter.recentRuns(gui, 'DevSecOps/CERT "gui"', 30, 25)

        then:
        1 * influx.query('''\
            import "strings"

            from(bucket: "DORA-metrics")
              |> range(start: -30d)
              |> filter(fn: (r) => r._measurement == "pipeline_run")
              |> filter(fn: (r) => r.project == "CERT-gui" and r.env == "test")
              |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
              |> filter(fn: (r) => exists r.job and (r.job == "DevSecOps/CERT \\"gui\\"" or strings.hasPrefix(v: r.job, prefix: "DevSecOps/CERT \\"gui\\"/")))
              |> group()
              |> sort(columns: ["_time"], desc: true)
              |> limit(n: 25)
            '''.stripIndent()) >> []
    }

    def "the project and the environment of a pipeline are escaped as Flux strings"() {
        when:
        adapter.latestRuns([new MetricsTag('CERT-"gui"${x}\\', 'test')], [] as Set)
        adapter.recentRuns(new MetricsTag('CERT-gui" or true or "', 'te"st'), null, 30, 25)
        adapter.doraPoints([new MetricsTag('CERT-"gui"${x}\\', 'test')], 30)

        then:
        2 * influx.query({ String flux -> flux.contains('set: ["CERT-\\"gui\\"\\${x}\\\\"]') }) >> []
        1 * influx.query({ String flux ->
            flux.contains('r.project == "CERT-gui\\" or true or \\"" and r.env == "te\\"st"')
        }) >> []
    }

    def "a window of #days days and #limit runs is not a query"() {
        when:
        adapter.recentRuns(gui, null, days, limit)

        then:
        0 * influx.query(_)
        def e = thrown(IllegalArgumentException)
        e.message.endsWith(' is not a positive number')

        where:
        days | limit
        0    | 25
        30   | 0
        -1   | 25
    }

    def "DORA points are read from the dora measurement of every tag asked for"() {
        when:
        def points = adapter.doraPoints([gui, guiSast], 90)

        then:
        1 * influx.query('''\
            from(bucket: "DORA-metrics")
              |> range(start: -90d)
              |> filter(fn: (r) => r._measurement == "dora")
              |> filter(fn: (r) => contains(value: r.project, set: ["CERT-gui", "CERT-guisast"]))
              |> filter(fn: (r) => r._field == "deployment" or r._field == "change_failure" or r._field == "lead_time_s" or r._field == "duration_s")
              |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
              |> group()
              |> sort(columns: ["_time"])
            '''.stripIndent()) >> [
                [_time: '2026-10-01T10:00:00Z', project: 'CERT-gui', env: 'test', deployment: '1',
                 change_failure: '0', lead_time_s: '3600', duration_s: '600.0'],
                [_time: '2026-10-02T10:00:00Z', project: 'CERT-gui', env: 'test', change_failure: '1'],
                [_time: '2026-10-02T11:00:00Z', project: 'CERT-guisast', env: 'test', deployment: '0'],
                [_time: '2026-10-02T12:00:00Z', project: 'CERT-gui', env: 'prod', deployment: '1'],
                [project: 'CERT-gui', env: 'test', deployment: '1']]
        points == [(gui)    : [new DoraPoint(Instant.parse('2026-10-01T10:00:00Z'), true, false, 3600, 600),
                               new DoraPoint(Instant.parse('2026-10-02T10:00:00Z'), false, true, 0, 0)],
                   (guiSast): [new DoraPoint(Instant.parse('2026-10-02T11:00:00Z'), false, false, 0, 0)]]
        adapter.doraPoints([], 90) == [:]
    }

    def "without InfluxDB nothing is read and the reason is given"() {
        given:
        def client = unconfigured()
        def unconfiguredAdapter = new InfluxPipelineRunsAdapter(client)

        when:
        reading(unconfiguredAdapter)

        then:
        0 * client.query(_)
        def e = thrown(UncheckedIOException)
        e.message == 'InfluxDB is not configured for the portal'

        where:
        reading << [{ it.latestRuns([], [] as Set) }, { it.latestRuns([new MetricsTag('CERT-gui', 'test')], [] as Set) },
                    { it.recentRuns(new MetricsTag('CERT-gui', 'test'), null, 30, 25) },
                    { it.doraPoints([new MetricsTag('CERT-gui', 'test')], 30) }, { it.doraPoints([], 30) },
                    { it.ping() }]
    }

    private InfluxQueryClient unconfigured() {
        Spy(InfluxQueryClient, constructorArgs: [
                new InfluxProperties(null, 'DevSecOps', 'DORA-metrics', null, '365d'), RestClient.builder()]) as InfluxQueryClient
    }

    private static Map<String, String> run(String time, String project, String env, String result) {
        [_time: time, project: project, env: env, result: result, build: '7', duration_s: '1200']
    }
}
