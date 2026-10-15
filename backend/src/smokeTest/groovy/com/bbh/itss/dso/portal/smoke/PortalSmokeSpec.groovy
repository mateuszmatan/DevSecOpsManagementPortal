package com.bbh.itss.dso.portal.smoke

import com.bbh.itss.dso.portal.DsoPortalApplication
import com.bbh.itss.dso.portal.support.PortalClient
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.yaml.snakeyaml.Yaml
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import java.nio.file.Path

import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ApiJson.service
import static java.nio.file.Files.createTempDirectory

@Stepwise
class PortalSmokeSpec extends Specification {

    static final List<String> DEPARTMENTS = ['AI Lab', 'Capital Partners', 'Corporate Technology', 'Custody',
                                             'Fund Services']

    @Shared
    Path dataDir = createTempDirectory('dso-portal-smoke')

    @Shared
    ConfigurableApplicationContext started

    @Shared
    PortalClient api

    def setupSpec() {
        String baseUrl = System.getProperty('smoke.baseUrl')
        api = new PortalClient(baseUrl ?: start())
    }

    def cleanupSpec() {
        started?.close()
        dataDir.toFile().deleteDir()
    }

    def "the application reports itself healthy"() {
        when:
        def health = api.get('/actuator/health')

        then:
        health.status == 200
        health.json.status == 'UP'
    }

    def "the product catalog and the departments answer, and a new portal starts with no products"() {
        when:
        def products = api.get('/api/products')
        def departments = api.get('/api/departments')

        then:
        products.status == 200
        products.json instanceof List
        departments.status == 200
        departments.json*.name.containsAll(DEPARTMENTS)
        !started || products.json.isEmpty() && departments.json.every {
            [it.productCount, it.serviceCount, it.pipelineCount, it.activePipelineCount] == [0, 0, 0, 0]
        }
    }

    @Requires({ !System.getProperty('smoke.baseUrl') })
    def "a product saved through the API is still there after a restart and its pipeline key reads its configuration"() {
        given:
        def created = api.post('/api/products', product(code: 'SMOKE', name: 'Smoke product',
                services: [service(name: 'gui')]))
        def key = api.get("/api/products/$created.json.id/pipelines").json[0].pipelines[0].activeKey.value

        when:
        started.close()
        api = new PortalClient(start())

        then:
        created.status == 201
        api.get("/api/products/$created.json.id").json.name == 'Smoke product'
        api.get("/api/dso/config/$key?format=json").status == 200
    }

    def "every product's services, pipelines and configuration can be read"() {
        given:
        def products = api.get('/api/products').json.take(5)

        expect:
        products.every { product ->
            def details = api.get("/api/products/$product.id")
            def pipelines = api.get("/api/products/$product.id/pipelines")
            def config = api.get("/api/products/$product.id/config")
            assert details.status == 200: details
            assert pipelines.status == 200: pipelines
            assert config.status == 200: config
            assert (new Yaml().load(config.body) as Map).projects.size() == details.json.services.size()
            true
        }
    }

    def "the global pipeline settings and the configuration they make can be read"() {
        when:
        def settings = api.get('/api/settings')
        def config = api.get('/api/settings/config')

        then:
        settings.status == 200
        settings.json.version >= 0
        settings.json.platform.asocUrl != null
        config.status == 200
        (new Yaml().load(config.body) as Map).keySet() == ['platform', 'defaults'] as Set
    }

    def "a pipeline key that was never issued is refused"() {
        expect:
        api.get('/api/dso/config/00000000-0000-4000-8000-000000000000').status == 404
    }

    def "the monitoring pages answer, with or without InfluxDB"() {
        when:
        def status = api.get('/api/monitoring/status')
        def overview = api.get('/api/monitoring/products')
        def activity = api.get('/api/monitoring/activity?range=30d')

        then:
        status.status == 200
        status.json.containsKey('influxConfigured')
        overview.status == 200
        overview.json.products instanceof List
        activity.status == 200
        activity.json.dora.daily.size() == 30
    }

    def "Beadle's change management is not part of this application"() {
        expect:
        ['/api/changes', '/api/change-profiles', '/api/evidence/products/1'].every { api.get(it).status == 404 }
    }

    @Requires({ PortalSmokeSpec.uiExpected() })
    def "the web UI is served, also for links into the app"() {
        expect:
        ['/', '/pipelines', '/self-service', '/monitoring', '/admin/departments', '/admin/products',
         '/admin/template', '/admin/settings'].every { path ->
            def page = api.get(path)
            assert page.status == 200: "$path: $page.status"
            assert page.header('Content-Type').startsWith('text/html')
            assert page.body.contains('<dso-root')
            true
        }
    }

    static boolean uiExpected() {
        System.getProperty('smoke.ui') != 'false' &&
                (System.getProperty('smoke.baseUrl') || PortalSmokeSpec.getResource('/static/index.html'))
    }

    private String start() {
        started = new SpringApplicationBuilder(DsoPortalApplication).run('--server.port=0', "--DSO_DATA_DIR=$dataDir")
        "http://localhost:${started.environment.getProperty('local.server.port')}"
    }
}
