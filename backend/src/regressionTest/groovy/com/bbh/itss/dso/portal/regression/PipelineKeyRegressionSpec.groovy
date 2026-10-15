package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.PortalSpecification
import org.yaml.snakeyaml.Yaml

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

import static com.bbh.itss.dso.portal.support.ApiJson.pipeline
import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ApiJson.service
import static java.util.concurrent.Executors.newFixedThreadPool
import static java.util.concurrent.TimeUnit.SECONDS

class PipelineKeyRegressionSpec extends PortalSpecification {

    static final int FETCHERS = 6

    String code
    Map certScanner
    long gui

    def setup() {
        code = uniqueCode()
        certScanner = createProduct(product(code: code, name: "Product $code",
                services: [service(name: 'gui'), service(name: 'backend-api')]))
        gui = certScanner.services[0].id as long
    }

    def "a new pipeline's key fetches its configuration until it is invalidated"() {
        given:
        def created = pipelineFor(gui, pipeline(agentLabels: ['linux-agent', 'docker']))
        String key = created.activeKey.value

        when:
        def config = api.get("/api/dso/config/$key")

        then:
        created.enabled
        key ==~ /[0-9a-f-]{36}/
        config.status == 200
        config.header('Content-Type').startsWith('application/yaml')
        def yaml = new Yaml().load(config.body) as Map
        yaml.pipeline == [type: 'full', entryPoint: 'devSecOpsPipeline', product: code, projectNames: 'gui',
                          agentNames: ['linux-agent', 'docker']]
        yaml.projects.keySet() == ['gui'] as Set
        yaml.projects.gui.influx.project == "$code-gui"

        when:
        def revoked = api.post("/api/pipelines/$created.id/keys/revoke", [reason: 'Leaked in a build log'])
        def refused = api.get("/api/dso/config/$key")

        then:
        revoked.status == 200
        !revoked.json.enabled
        revoked.json.activeKey == null
        refused.status == 403
        refused.json.title == 'Pipeline key invalidated'
        refused.json.detail.endsWith(': Leaked in a build log')
    }

    def "a pipeline links its Jenkins job given as a URL; a path needs the Jenkins URL of the global settings"() {
        when:
        def byUrl = pipelineFor(gui, pipeline(jenkinsJob: 'https://jenkins.bbh.com/job/DevSecOps/job/gui-full/',
                description: 'Nightly full pipeline'))
        def byPath = pipelineFor(gui, pipeline(type: 'SAST', jenkinsJob: "DevSecOps/$code/gui-sast"))

        then:
        byUrl.jenkinsJobUrl == 'https://jenkins.bbh.com/job/DevSecOps/job/gui-full/'
        byUrl.description == 'Nightly full pipeline'
        byPath.jenkinsJob == "DevSecOps/$code/gui-sast"
        byPath.jenkinsJobUrl == null
    }

    def "a new key replaces the old one, which stays refused"() {
        given:
        def created = pipelineFor(gui)
        String oldKey = created.activeKey.value

        when:
        def reissued = api.post("/api/pipelines/$created.id/keys")
        String newKey = reissued.json.activeKey.value

        then:
        reissued.status == 200
        newKey != oldKey
        reissued.json.keys*.status == ['ACTIVE', 'REVOKED']
        reissued.json.keys[1].revokeReason == 'Replaced by a new key'
        api.get("/api/dso/config/$oldKey").status == 403
        api.get("/api/dso/config/$newKey").status == 200
    }

    def "every key shows its hint, only the active key its value, and the monitoring pages no value at all"() {
        given:
        def created = pipelineFor(gui)
        String oldKey = created.activeKey.value
        String newKey = api.post("/api/pipelines/$created.id/keys").json.activeKey.value

        when:
        def details = api.get("/api/pipelines/$created.id").json
        def listed = api.get("/api/products/$certScanner.id/pipelines").json[0].pipelines[0]
        def productPage = api.get("/api/monitoring/products/$certScanner.id")
        def pipelinePage = api.get("/api/monitoring/pipelines/$created.id")

        then:
        details.keys*.value == [newKey, null]
        details.keys*.hint == [hint(newKey), hint(oldKey)]
        details.activeKey.value == newKey
        listed.activeKey.value == newKey
        listed.activeKey.hint == hint(newKey)

        and:
        with(productPage.json.pipelines[0].pipeline) {
            activeKey.value == null
            activeKey.hint == hint(newKey)
            activeKey.status == 'ACTIVE'
            keys == []
        }
        with(pipelinePage.json.pipeline) {
            activeKey.value == null
            activeKey.hint == hint(newKey)
            keys == []
        }
        [productPage, pipelinePage].every { !it.body.contains(newKey) && !it.body.contains(oldKey) }
    }

    def "fetching the configuration records when the key was last used"() {
        given:
        def created = pipelineFor(gui)

        when:
        api.get("/api/dso/config/${created.activeKey.value.toUpperCase()}?format=json")

        then:
        created.activeKey.lastUsedAt == null
        api.get("/api/pipelines/$created.id").json.activeKey.lastUsedAt != null
    }

    def "an unknown key is 404 and revoking twice is 409"() {
        given:
        def created = pipelineFor(gui)
        api.post("/api/pipelines/$created.id/keys/revoke", [reason: 'retired'])

        expect:
        api.get('/api/dso/config/00000000-0000-4000-8000-000000000000').status == 404
        api.post("/api/pipelines/$created.id/keys/revoke", [reason: 'again']).status == 409
    }

    def "agent labels beyond their column are refused with a field problem"() {
        when:
        def response = api.post("/api/services/$gui/pipelines",
                pipeline(type: 'SAST', agentLabels: (1..20).collect { "agent-$it-${'x' * 50}".toString() }))

        then:
        response.status == 400
        response.json.errors.collect { [it.field, it.message] } ==
                [['agentLabels', 'is too long: all entries together may take at most 1000 bytes']]
        api.get("/api/products/$certScanner.id/pipelines").json[0].pipelines*.type == ['FULL']
    }

    def "a pipeline is changed at the version it was read at, and a change read before another one is refused"() {
        given:
        def read = pipelineFor(gui)

        when:
        def first = api.put("/api/pipelines/$read.id", pipeline(jenkinsJob: "DevSecOps/$code/gui-full", version: read.version))
        def stale = api.put("/api/pipelines/$read.id", pipeline(agentLabels: ['windows'], version: read.version))
        def unversioned = api.put("/api/pipelines/$read.id", pipeline(description: 'Nightly'))

        then:
        first.status == 200
        first.json.version == read.version + 1
        stale.status == 409
        stale.json.detail == 'The record was changed by someone else in the meantime. Reload it and apply your change again.'
        unversioned.status == 200
        unversioned.json.version == first.json.version + 1
        unversioned.json.agentLabels == ['linux-agent']
        api.get("/api/pipelines/$read.id").json.version == unversioned.json.version
        api.get("/api/products/$certScanner.id/pipelines").json[0].pipelines[0].version == unversioned.json.version
    }

    def "texts that fit their column in characters but not in UTF-8 bytes are refused against their fields"() {
        given:
        def created = pipelineFor(gui)

        when:
        def settings = api.put("/api/pipelines/$created.id", pipeline(jenkinsJob: 'Zespół/' * 125,
                description: 'Opis zadań – ' * 70))
        def revoked = api.post("/api/pipelines/$created.id/keys/revoke", [reason: 'Klucz wyciekł – ' * 30])

        then:
        settings.status == 400
        settings.json.errors == [[field: 'jenkinsJob', message: 'is too long: it may take at most 1000 bytes'],
                                 [field: 'description', message: 'is too long: it may take at most 1000 bytes']]
        revoked.status == 400
        revoked.json.errors == [[field: 'reason', message: 'is too long: it may take at most 500 bytes']]
        api.get("/api/pipelines/$created.id").json.with { [jenkinsJob, description, enabled] } ==
                [created.jenkinsJob, created.description, true]
    }

    def "a service has one pipeline of each type and a pipeline keeps its type"() {
        given:
        def full = pipelineFor(gui)

        expect:
        api.post("/api/services/$gui/pipelines", pipeline()).status == 409
        api.put("/api/pipelines/$full.id", pipeline(type: 'SAST')).status == 409
        pipelineFor(gui, pipeline(type: 'SAST')).type == 'SAST'
    }

    def "the security pipeline passes on its extended pipeline job"() {
        when:
        def security = pipelineFor(gui, pipeline(type: 'SECURITY', extendedPipelineJob: "$code/gui-extended"))
        def full = pipelineFor(gui, pipeline(type: 'FULL', extendedPipelineJob: 'ignored'))
        def config = new Yaml().load(api.get("/api/dso/config/$security.activeKey.value").body) as Map

        then:
        security.influxProjectTag == "$code-guisecurity"
        full.extendedPipelineJob == null
        config.projects.gui.jenkins.pipeline.extendedPipeline == "$code/gui-extended"
    }

    def "the Nexus IQ GoldenFix pipeline links no other job and reads its runs under its own project tag"() {
        when:
        def nexusIq = pipelineFor(gui, pipeline(type: 'NEXUS_IQ', jenkinsJob: "DevSecOps/$code/gui-nexusiq",
                extendedPipelineJob: "$code/gui-extended", securityPipelineJob: "$code/gui-security"))
        def config = new Yaml().load(api.get("/api/dso/config/$nexusIq.activeKey.value").body) as Map

        then:
        nexusIq.type == 'NEXUS_IQ'
        nexusIq.entryPoint == 'devSecOpsNexusIqGoldenFixPipeline'
        nexusIq.influxProjectTag == "$code-guinexusiq"
        nexusIq.jenkinsJob == "DevSecOps/$code/gui-nexusiq"
        [nexusIq.extendedPipelineJob, nexusIq.securityPipelineJob] == [null, null]
        config.pipeline == [type: 'nexusiq', entryPoint: 'devSecOpsNexusIqGoldenFixPipeline', product: code,
                            projectNames: 'gui', agentNames: ['linux-agent']]
        !config.projects.gui.containsKey('jenkins')
    }

    def "the product's pipelines are listed per service"() {
        given:
        pipelineFor(gui)
        pipelineFor(gui, pipeline(type: 'SAST'))

        when:
        def list = api.get("/api/products/$certScanner.id/pipelines").json

        then:
        list*.serviceName == ['gui', 'backend-api']
        list[0].pipelines*.type == ['FULL', 'SAST']
        list[1].pipelines*.type == ['FULL']
        api.get('/api/products').json.find { it.code == code }.pipelineCount == 3
    }

    def "removing a service from the product removes its pipelines"() {
        given:
        def created = pipelineFor(certScanner.services[1].id as long)

        when:
        api.put("/api/products/$certScanner.id", product(code: code, name: "Product $code",
                services: [service(id: gui, name: 'gui')]))

        then:
        api.get("/api/pipelines/$created.id").status == 404
        api.get("/api/dso/config/$created.activeKey.value").status == 404
    }

    def "concurrent key changes leave exactly one active key"() {
        given:
        long id = pipelineFor(gui).id as long
        def pool = newFixedThreadPool(10)

        when:
        def statuses = pool.invokeAll((1..20).collect { { -> api.post("/api/pipelines/$id/keys").status } as Callable<Integer> })*.get()
        def keys = api.get("/api/pipelines/$id").json.keys

        then:
        statuses.every { it == 200 }
        keys.size() == 21
        keys.count { it.status == 'ACTIVE' } == 1

        cleanup:
        pool.shutdownNow()
    }

    def "a key invalidated while pipelines keep fetching its configuration stays invalidated"() {
        given:
        def raced = createProduct(product(code: uniqueCode('RACE'), name: "Race ${uniqueCode()}",
                services: (1..5).collect { service(name: "svc-$it") }))
        def pool = newFixedThreadPool(FETCHERS)

        when:
        def outcomes = raced.services.collect { svc ->
            def created = pipelineFor(svc.id as long)
            String key = created.activeKey.value
            def fetches = fetchDuring(pool, key) {
                assert api.post("/api/pipelines/$created.id/keys/revoke", [reason: 'Leaked in a build log']).status == 200
            }
            [pipeline: api.get("/api/pipelines/$created.id").json, key: key, fetches: fetches,
             refused: api.get("/api/dso/config/$key")]
        }

        then:
        outcomes.every { outcome ->
            outcome.fetches.every { it.status in [200, 403] } &&
                    outcome.fetches.findAll { it.afterChange }.every { it.status == 403 } &&
                    outcome.pipeline.keys*.status == ['REVOKED'] &&
                    outcome.pipeline.keys[0].revokeReason == 'Leaked in a build log' &&
                    outcome.pipeline.activeKey == null &&
                    outcome.refused.status == 403
        }

        cleanup:
        pool.shutdownNow()
    }

    def "a key replaced while pipelines keep fetching its configuration leaves one active key"() {
        given:
        def created = pipelineFor(gui)
        def pool = newFixedThreadPool(FETCHERS)
        List<String> replaced = []

        when:
        String key = created.activeKey.value
        5.times {
            String current = key
            fetchDuring(pool, current) {
                key = api.post("/api/pipelines/$created.id/keys").json.activeKey.value
            }
            replaced << current
        }
        def keys = api.get("/api/pipelines/$created.id").json.keys

        then:
        keys*.status == ['ACTIVE'] + ['REVOKED'] * 5
        keys[0].value == key
        jdbc.queryForObject("SELECT COUNT(*) FROM DSO_PIPELINE_KEY WHERE PIPELINE_ID = ? AND STATUS = 'ACTIVE'",
                Integer, created.id) == 1
        replaced.every { api.get("/api/dso/config/$it").status == 403 }
        api.get("/api/dso/config/$key").status == 200

        cleanup:
        pool.shutdownNow()
    }

    def "an invalidated key is regenerated as a new active key while the old keys stay refused"() {
        given:
        def created = pipelineFor(gui)
        String first = created.activeKey.value
        String second = api.post("/api/pipelines/$created.id/keys").json.activeKey.value
        api.post("/api/pipelines/$created.id/keys/revoke", [reason: 'Leaked in a build log'])

        when:
        def regenerated = api.post("/api/pipelines/$created.id/keys")
        String third = regenerated.json.activeKey.value

        then:
        regenerated.status == 200
        third != first && third != second
        regenerated.json.keys*.status == ['ACTIVE', 'REVOKED', 'REVOKED']
        regenerated.json.keys*.revokeReason == [null, 'Leaked in a build log', 'Replaced by a new key']
        regenerated.json.keys*.value == [third, null, null]
        regenerated.json.keys*.hint == [hint(third), hint(second), hint(first)]
        api.get("/api/dso/config/$first").status == 403
        api.get("/api/dso/config/$second").status == 403
        api.get("/api/dso/config/$third").status == 200
    }

    private List<Map> fetchDuring(ExecutorService pool, String key, Closure change) {
        def changed = new AtomicBoolean()
        def stop = new AtomicBoolean()
        def warmedUp = new CountDownLatch(FETCHERS)
        def settled = new CountDownLatch(FETCHERS)
        def fetchers = (1..FETCHERS).collect {
            pool.submit({ ->
                def client = api
                List<Map> fetches = []
                while (!stop.get()) {
                    boolean afterChange = changed.get()
                    fetches << [afterChange: afterChange, status: client.get("/api/dso/config/$key").status]
                    if (fetches.size() == 3) {
                        warmedUp.countDown()
                    }
                    if (fetches.count { it.afterChange } == 3) {
                        settled.countDown()
                    }
                }
                fetches
            } as Callable<List<Map>>)
        }
        assert warmedUp.await(30, SECONDS)
        change()
        changed.set(true)
        assert settled.await(30, SECONDS)
        stop.set(true)
        fetchers.collectMany { it.get() }
    }

    private static String hint(String key) {
        "${key.take(8)}\u2026${key[-4..-1]}"
    }
}
