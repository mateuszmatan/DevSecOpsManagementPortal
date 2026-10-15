package com.bbh.itss.dso.portal.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonSlurper
import groovy.transform.Canonical

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService

import static java.lang.Runtime.getRuntime
import static java.net.InetAddress.getLoopbackAddress
import static java.nio.charset.StandardCharsets.UTF_8
import static java.time.Duration.ofDays
import static java.util.concurrent.Executors.newFixedThreadPool

class FakeInfluxDb implements AutoCloseable {

    static final List<String> RUN_COLUMNS = ['_time', 'project', 'env', 'variant', 'result', 'branch', 'build',
                                             'duration_s', 'commit', 'job', 'stages_total', 'passed', 'warned',
                                             'failed', 'blocked', 'skipped']
    static final List<String> DORA_COLUMNS = ['_time', 'project', 'env', 'variant', 'change_failure', 'deployment',
                                              'duration_s', 'lead_time_s']

    private static FakeInfluxDb sharedInstance

    final List<Request> requests = new CopyOnWriteArrayList<>()

    private final List<Run> runs = new CopyOnWriteArrayList<>()
    private final HttpServer server
    private final ExecutorService executor = newFixedThreadPool(16, { Runnable task ->
        Thread thread = new Thread(task, 'fake-influxdb')
        thread.daemon = true
        thread
    })
    private volatile String fixedAnswer
    private volatile int status = 200
    private volatile Closure queryListener

    private FakeInfluxDb() {
        server = HttpServer.create(new InetSocketAddress(getLoopbackAddress(), 0), 0)
        server.executor = executor
        server.createContext('/api/v2/query') { HttpExchange exchange -> handle(exchange) }
        server.start()
    }

    static FakeInfluxDb start() {
        new FakeInfluxDb()
    }

    static synchronized FakeInfluxDb shared() {
        if (!sharedInstance) {
            sharedInstance = start()
            getRuntime().addShutdownHook(new Thread({ sharedInstance.close() }))
        }
        sharedInstance
    }

    String getUrl() {
        "http://127.0.0.1:${server.address.port}"
    }

    void respondWith(String csv) {
        fixedAnswer = csv
    }

    void failWith(int status) {
        this.status = status
    }

    void onQuery(Closure listener) {
        queryListener = listener
    }

    void addRun(Map args) {
        String result = args.result ?: 'SUCCESS'
        runs << new Run(project: args.project, env: args.env ?: 'test', variant: args.variant ?: 'full',
                time: args.time as Instant ?: Instant.now(), result: result, branch: args.branch ?: 'develop',
                job: args.job as String,
                build: args.build as Long ?: runs.size() + 1, durationSeconds: args.durationSeconds as Long ?: 600,
                deployment: args.containsKey('deployment') ? args.deployment : result == 'SUCCESS',
                leadTimeSeconds: args.leadTimeSeconds as Long ?: 3600)
    }

    void reset() {
        runs.clear()
        requests.clear()
        fixedAnswer = null
        status = 200
        queryListener = null
    }

    @Override
    void close() {
        server.stop(0)
        executor.shutdownNow()
    }

    private void handle(HttpExchange exchange) {
        try {
            queryListener?.call()
            Map body = new JsonSlurper().parse(exchange.requestBody) as Map
            requests << new Request(method: exchange.requestMethod, path: exchange.requestURI.path,
                    query: exchange.requestURI.query, authorization: exchange.requestHeaders.getFirst('Authorization'),
                    contentType: exchange.requestHeaders.getFirst('Content-Type'),
                    accept: exchange.requestHeaders.getFirst('Accept'), body: body)
            String answer = status == 200 ? (fixedAnswer ?: answer(body.query as String))
                    : '{"code":"unauthorized","message":"unauthorized access"}'
            byte[] bytes = answer.getBytes(UTF_8)
            exchange.responseHeaders.add('Content-Type', status == 200 ? 'text/csv; charset=utf-8' : 'application/json')
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length)
            exchange.responseBody.withStream { it.write(bytes) }
        } finally {
            exchange.close()
        }
    }

    private String answer(String flux) {
        if (flux.startsWith('buckets()')) {
            return ',result,table,name\r\n,_result,0,DORA-metrics\r\n'
        }
        if (flux.contains('last(column: "_time")')) {
            return latestRuns(flux)
        }
        if (flux.contains('"pipeline_run"')) {
            return recentRuns(flux)
        }
        if (flux.contains('"dora"')) {
            return doraPoints(flux)
        }
        return ''
    }

    private String latestRuns(String flux) {
        Set<String> projects = projectsIn(flux)
        Instant since = Instant.now() - ofDays(days(flux))
        boolean perJob = flux.contains('group(columns: ["project", "env", "job"])')
        def newest = runs.findAll { it.project in projects && it.time.isAfter(since) }
                .groupBy { perJob ? [it.project, it.env, jobOf(it)] : [it.project, it.env] }
                .values()
                .collect { it.max { run -> run.time } }
        csv(RUN_COLUMNS, newest.withIndex().collect { run, table -> [table, runValues(run)] })
    }

    private String recentRuns(String flux) {
        int limit = (flux =~ /limit\(n: (\d+)\)/)[0][1] as int
        def selected = selectedRuns(flux).sort { -it.time.toEpochMilli() }.take(limit)
        csv(RUN_COLUMNS, selected.collect { [0, runValues(it)] })
    }

    private String doraPoints(String flux) {
        Set<String> projects = projectsIn(flux)
        Instant since = Instant.now() - ofDays(days(flux))
        def selected = runs.findAll { it.project in projects && it.time.isAfter(since) }.sort { it.time }
        csv(DORA_COLUMNS, selected.collect { run ->
            [0, [run.time.toString(), run.project, run.env, run.variant, run.result == 'SUCCESS' ? '0' : '1',
                 run.deployment ? '1' : '0', run.durationSeconds as String, run.leadTimeSeconds as String]]
        })
    }

    private List<Run> selectedRuns(String flux) {
        def filter = (flux =~ /r\.project == "([^"]*)" and r\.env == "([^"]*)"/)[0]
        def job = (flux =~ /r\.job == "([^"]*)"/).with { it.find() ? it.group(1) : null }
        Instant since = Instant.now() - ofDays(days(flux))
        runs.findAll {
            it.project == filter[1] && it.env == filter[2] && it.time.isAfter(since) &&
                    (job == null || jobOf(it) == job || jobOf(it).startsWith(job + '/'))
        }
    }

    private static Set<String> projectsIn(String flux) {
        (flux =~ /set: \[(.*?)]/)[0][1].findAll(/"([^"]*)"/) { all, value -> value } as Set
    }

    private static String jobOf(Run run) {
        run.job ?: "${run.project}/${run.variant}".toString()
    }

    private static long days(String flux) {
        (flux =~ /range\(start: -(\d+)d\)/)[0][1] as long
    }

    private static List<String> runValues(Run run) {
        [run.time.toString(), run.project, run.env, run.variant, run.result, run.branch, run.build as String,
         run.durationSeconds as String, 'a1b2c3d4e5f6', jobOf(run), '12',
         run.result == 'SUCCESS' ? '12' : '11', run.result == 'UNSTABLE' ? '1' : '0', run.result == 'FAILURE' ? '1' : '0',
         '0', '0']
    }

    private static String csv(List<String> columns, List<List> tablesAndValues) {
        if (tablesAndValues.isEmpty()) {
            return ''
        }
        def lines = [',result,table,' + columns.join(',')]
        tablesAndValues.each { table, values -> lines << ",_result,$table," + values.join(',') }
        lines.join('\r\n') + '\r\n'
    }

    @Canonical
    static class Request {
        String method
        String path
        String query
        String authorization
        String contentType
        String accept
        Map body
    }

    @Canonical
    static class Run {
        String project
        String env
        String variant
        Instant time
        String result
        String branch
        Long build
        Long durationSeconds
        boolean deployment
        Long leadTimeSeconds
        String job
    }
}
