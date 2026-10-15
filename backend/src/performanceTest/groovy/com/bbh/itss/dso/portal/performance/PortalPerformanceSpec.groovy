package com.bbh.itss.dso.portal.performance

import com.bbh.itss.dso.portal.support.FakeInfluxDb
import com.bbh.itss.dso.portal.support.LatencyStats
import com.bbh.itss.dso.portal.support.PortalSpecification
import spock.lang.Shared
import spock.lang.Stepwise

import java.time.Instant

import static com.bbh.itss.dso.portal.support.ApiJson.fullMavenService
import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.LatencyStats.measure
import static java.time.Duration.ofHours

@Stepwise
class PortalPerformanceSpec extends PortalSpecification {

    static final int PRODUCTS = 25
    static final int SERVICES = 16
    static final double FACTOR = (System.getProperty('performance.factor') ?: '1') as double
    static final File REPORT = new File(System.getProperty('performance.report', 'build/reports/performance/performance-report.md'))

    @Shared
    List<Map> products = [].asSynchronized()

    @Shared
    List<Map> pipelines = [].asSynchronized()

    @Shared
    List<LatencyStats> results = []

    def setupSpec() {
        FakeInfluxDb.shared().reset()
    }

    def cleanupSpec() {
        REPORT.parentFile.mkdirs()
        REPORT.text = "# Portal performance\n\n$PRODUCTS products with $SERVICES services each, " +
                "${2 * PRODUCTS * SERVICES} pipelines, limits scaled by $FACTOR.\n\n" +
                LatencyStats.header() + '\n' + results*.toRow().join('\n') + '\n'
        println REPORT.text
    }

    def "products with 16 services are created quickly"() {
        when:
        def stats = measure("Create a product with $SERVICES services", calls: PRODUCTS, warmUp: false) { int i ->
            def code = uniqueCode('PERF')
            def response = api.post('/api/products', product(code: code, name: "Performance $code",
                    departmentId: i % 5 + 1, services: (1..SERVICES).collect { fullService(code, "service-$it") }))
            if (response.status == 201) {
                products << (response.json as Map)
            }
            response.status == 201
        }

        then:
        within(stats, 2000)
        products.size() == PRODUCTS
    }

    def "a second pipeline is created for every service that already started with one"() {
        given:
        def serviceIds = products.collectMany { it.services*.id }
        products.each { product ->
            api.get("/api/products/$product.id/pipelines").json.each { service ->
                pipelines.addAll(service.pipelines as List<Map>)
            }
        }

        when:
        def stats = measure('Create a pipeline', calls: serviceIds.size(), threads: 8, warmUp: false) { int i ->
            def response = api.post("/api/services/${serviceIds[i]}/pipelines", [type: 'SAST', agentLabels: ['linux-agent']])
            if (response.status == 201) {
                pipelines << (response.json as Map)
            }
            response.status == 201
        }

        then:
        within(stats, 500)
        pipelines.size() == 2 * PRODUCTS * SERVICES
        pipelines.count { it.type == 'FULL' } == PRODUCTS * SERVICES
        pipelines.every { it.activeKey.value != null }
    }

    def "the product list, the departments and a product's details stay fast"() {
        when:
        def list = measure('List all products', calls: 100, threads: 8) { api.get('/api/products').status == 200 }
        def departments = measure('List departments with their pipeline counts', calls: 100, threads: 8) {
            def response = api.get('/api/departments')
            response.status == 200 && response.json.sum { it.pipelineCount } == pipelines.size()
        }
        def details = measure("Read a product with $SERVICES services", calls: 200, threads: 8) { int i ->
            api.get("/api/products/${products[i % PRODUCTS].id}").status == 200
        }
        def productPipelines = measure("List a product's pipelines", calls: 200, threads: 8) { int i ->
            api.get("/api/products/${products[i % PRODUCTS].id}/pipelines").status == 200
        }

        then:
        within(list, 500)
        within(departments, 500)
        within(details, 300)
        within(productPipelines, 300)
    }

    def "a burst of pipelines fetching their configuration is served"() {
        given:
        def keys = pipelines*.activeKey*.value

        when:
        def stats = measure('Fetch a configuration by key as the library does, 32 at once', calls: 2000, threads: 32) { int i ->
            api.get("/api/dso/config/${keys[i % keys.size()]}?format=json").status == 200
        }

        then:
        within(stats, 400)
    }

    def "a global settings change is saved and the next read of every pipeline sees it"() {
        given:
        Map original = api.get('/api/settings').json as Map

        when:
        def stats = measure("Change global settings, ${pipelines.size()} pipelines", calls: 5,
                warmUp: false) { int i ->
            def current = api.get('/api/settings').json
            api.put('/api/settings', original + [version: current.version,
                                                 scans  : original.scans + [coverageMinLine: 61 + i]]).status == 200
        }

        then:
        within(stats, 1000)
        api.get("/api/dso/config/${pipelines.last().activeKey.value}?format=json").json.defaults.coverage.minLine == 65

        cleanup:
        def current = api.get('/api/settings').json
        api.put('/api/settings', original + [version: current.version])
    }

    def "the monitoring pages stay fast with every pipeline reporting"() {
        given:
        def now = Instant.now()
        pipelines.each { influx.addRun(project: it.influxProjectTag, env: it.influxEnv, time: now - ofHours(3)) }
        def history = pipelines.first()
        (1..269).each { influx.addRun(project: history.influxProjectTag, env: history.influxEnv,
                time: now - ofHours(7 * it), result: it % 5 == 0 ? 'FAILURE' : 'SUCCESS') }

        when:
        def overview = measure("Monitoring overview of ${pipelines.size()} pipelines", calls: 50, threads: 4) {
            def response = api.get('/api/monitoring/products')
            response.status == 200 && response.json.metricsError == null
        }
        def details = measure('Pipeline details with 90 days of runs', calls: 100, threads: 8) {
            def response = api.get("/api/monitoring/pipelines/$history.id?range=90d")
            response.status == 200 && response.json.dora.runs == 270
        }

        then:
        within(overview, 1500)
        within(details, 800)
    }

    def "concurrent key changes keep one active key per pipeline"() {
        given:
        def targets = pipelines.take(10)*.id

        when:
        def stats = measure('Issue a new key, 10 at once', calls: 100, threads: 10) { int i ->
            api.post("/api/pipelines/${targets[i % targets.size()]}/keys").status == 200
        }

        then:
        within(stats, 1000)
        targets.every { id -> api.get("/api/pipelines/$id").json.keys.count { it.status == 'ACTIVE' } == 1 }
    }

    private static Map fullService(String code, String name) {
        String tag = "$code-$name"
        fullMavenService(name: name, metrics: [enabled: true, influxProject: tag, influxEnv: 'uat'],
                sonar: fullMavenService().sonar + [projectKey: tag],
                nexusIqApplications: [fullMavenService().nexusIqApplications[0] + [application: tag]])
    }

    private boolean within(LatencyStats stats, double p95LimitMillis) {
        results << stats
        assert stats.errors == 0: "${stats.name}: ${stats.errors} failed calls"
        assert stats.p95 <= p95LimitMillis * FACTOR: "${stats.name}: p95 ${stats.p95} ms is over ${p95LimitMillis * FACTOR} ms"
        true
    }
}
