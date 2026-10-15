package com.bbh.itss.dso.portal.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

import static com.bbh.itss.dso.portal.support.ApiJson.pipeline
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = [
        'spring.datasource.url=jdbc:h2:mem:dso-portal;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000',
        'dso.influx.token=test-token',
        'dso.grafana.instances[0].name=Grafana test',
        'dso.grafana.instances[0].dashboard-url=http://grafana.test/d/adzfc54123/devsecops-pipeline-long?orgId=1',
        'dso.grafana.instances[0].security-dashboard-url=http://grafana.test/d/ad2trcm/devsecops-security',
        'dso.grafana.instances[1].name=Grafana prod',
        'dso.grafana.instances[1].dashboard-url=http://grafana-prod.test/d/adzfc54123/devsecops-pipeline-long'])
abstract class PortalSpecification extends ServiceSpecification {

    FakeInfluxDb getInflux() {
        FakeInfluxDb.shared()
    }

    @DynamicPropertySource
    static void influxUrl(DynamicPropertyRegistry registry) {
        registry.add('dso.influx.url') { FakeInfluxDb.shared().url }
    }

    Map createPipeline(long serviceId, Map pipeline = pipeline()) {
        def response = api.post("/api/services/$serviceId/pipelines", pipeline)
        assert response.status == 201: response
        response.json as Map
    }

    Map pipelineFor(long serviceId, Map settings = pipeline()) {
        Map started = pipelineOfService(serviceId, (settings.type ?: 'FULL') as String)
        if (!started) {
            return createPipeline(serviceId, settings)
        }
        def response = api.put("/api/pipelines/$started.id", settings)
        assert response.status == 200: response
        response.json as Map
    }

    Map pipelineOfService(long serviceId, String type = 'FULL') {
        Long productId = jdbc.queryForObject('SELECT PRODUCT_ID FROM DSO_SERVICE WHERE ID = ?', Long, serviceId)
        api.get("/api/products/$productId/pipelines").json
                .collectMany { it.pipelines }
                .find { it.serviceId == serviceId && it.type == type } as Map
    }
}
