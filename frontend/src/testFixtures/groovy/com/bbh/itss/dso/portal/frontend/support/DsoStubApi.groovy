package com.bbh.itss.dso.portal.frontend.support

import static com.bbh.itss.dso.portal.frontend.support.StubResponse.json
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.yaml

class DsoStubApi extends StubApi {

    @Override
    void install() {
        installDepartments(serviceCount: 0, pipelineCount: 0, activePipelineCount: 0)
        get('/api/products') { RecordedRequest request ->
            def search = request.params().search?.toLowerCase()
            def products = fixture('products.json') as List<Map>
            json(search ? products.findAll {
                [it.code, it.name, it.ownerTeam, it.departmentName].any { value -> value?.toString()?.toLowerCase()?.contains(search) }
            } : products)
        }
        get('/api/products/code-suggestion') { RecordedRequest request ->
            json([code: request.params().name.toUpperCase().replaceAll(/[^A-Z0-9]/, '')])
        }
        get('/api/products/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("product-${ids[0]}.json", "Product ${ids[0]} does not exist") }
        get('/api/products/(\\d+)/pipelines') { RecordedRequest request, List<String> ids -> fixtureOr404("product-${ids[0]}-pipelines.json", "Product ${ids[0]} does not exist") }
        get('/api/products/(\\d+)/config') { RecordedRequest request, List<String> ids -> fixtureOr404("product-${ids[0]}-config.yaml", "Product ${ids[0]} does not exist") }
        get('/api/pipelines/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("pipeline-${ids[0]}.json", "Pipeline ${ids[0]} does not exist") }
        get('/api/pipelines/(\\d+)/config') { RecordedRequest request, List<String> ids -> fixtureOr404("pipeline-${ids[0]}-config.yaml", "Pipeline ${ids[0]} does not exist") }
        get('/api/pipelines') { RecordedRequest request ->
            def departmentId = request.params().departmentId as Integer
            if (!departments.any { it.id == departmentId }) {
                return problem(404, 'Not found', "Department $departmentId does not exist")
            }
            def products = (fixture('products.json') as List<Map>).findAll { it.departmentId == departmentId }
            def pipelines = products.collectMany { product ->
                hasFixture("monitoring-product-${product.id}.json") ? fixture("monitoring-product-${product.id}.json").pipelines as List : []
            }
            json([pipelines: pipelines, metricsError: null])
        }
        def template = fixture('service-template.json') as Map
        get('/api/service-template') { json(template) }
        on('PUT', '/api/service-template') { RecordedRequest request ->
            def sent = request.json() as Map
            if (sent.version != template.version) {
                return problem(409, 'Conflict', 'The service template was changed by someone else')
            }
            template.putAll(sent)
            template.putAll(version: (template.version as int) + 1, updatedAt: '2026-10-08T13:00:00Z')
            json(template)
        }
        get('/api/settings') { json(fixture('settings.json')) }
        get('/api/settings/config') { yaml(fixtureText('settings-config.yaml')) }
        get('/api/monitoring/status') { json(fixture('monitoring-status.json')) }
        get('/api/monitoring/products') { json(fixture('monitoring-products.json')) }
        get('/api/monitoring/activity') { json(fixture('monitoring-activity.json')) }
        get('/api/monitoring/products/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("monitoring-product-${ids[0]}.json", "Product ${ids[0]} does not exist") }
        get('/api/monitoring/pipelines/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("monitoring-pipeline-${ids[0]}.json", "Pipeline ${ids[0]} does not exist") }
    }
}
