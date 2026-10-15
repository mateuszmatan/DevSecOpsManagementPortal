package com.bbh.itss.dso.portal.frontend.performance

import com.bbh.itss.dso.portal.frontend.support.StubApi
import com.bbh.itss.dso.portal.frontend.support.StubResponse

import static com.bbh.itss.dso.portal.frontend.support.ApiData.ENTRY_POINTS
import static com.bbh.itss.dso.portal.frontend.support.ApiData.activeKey
import static com.bbh.itss.dso.portal.frontend.support.ApiData.keyValue
import static com.bbh.itss.dso.portal.frontend.support.ApiData.newPipeline
import static com.bbh.itss.dso.portal.frontend.support.ApiData.servicePipelines
import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.json

class LargeCatalogue {

    static final int PRODUCTS = 25
    static final int SERVICES = 16
    static final Map<String, String> TYPES = ENTRY_POINTS.subMap(['FULL', 'SECURITY', 'EXTENDED', 'SAST'])
    static final int PIPELINES = SERVICES * TYPES.size()

    private final Map product = fixture('product-2.json') as Map
    private final List<Map> departments = fixture('departments.json') as List<Map>
    private final Map lastRun = (fixture('monitoring-product-2.json') as Map).pipelines[0].lastRun as Map
    private final Map<String, StubResponse> responses = [:]

    LargeCatalogue() {
        def summaries = []
        def overview = []
        (1..PRODUCTS).each { int id ->
            def department = departments[(id - 1) % departments.size()]
            def facts = [code       : "CAT$id".toString(), name: String.format('Catalogue Product %02d', id),
                         description: "Generated product $id of the performance catalogue".toString(), ownerTeam: product.ownerTeam]
            def services = (0..<SERVICES).collect { int index -> service(id, index) }
            def pipelines = services.collect { Map service -> TYPES.keySet().withIndex().collect { String type, int index -> pipelineOf(id, facts, service, type, index) } }
            def all = pipelines.flatten() as List<Map>
            def counts = all.countBy { status(it) }
            def overall = ['FAILURE', 'UNSTABLE', 'SUCCESS'].find { it in counts }
            summaries << facts + [id          : id, departmentId: department.id, departmentName: department.name, serviceCount: SERVICES,
                                  pipelineCount: PIPELINES, activePipelineCount: all.count { it.enabled }, updatedAt: product.updatedAt]
            overview << facts + [productId: id, departmentId: department.id, serviceCount: SERVICES, pipelineCount: PIPELINES, overall: overall,
                                 statusCounts: counts, lastRunAt: lastRun.time]
            store("/api/products/$id", product + facts + [id: id, departmentId: department.id, services: services])
            store("/api/products/$id/pipelines", [services, pipelines].transpose().collect { Map service, List<Map> own -> servicePipelines(service, own) })
            store("/api/monitoring/products/$id", facts + [productId: id, overall: overall, metricsError: null, pipelines: all.collect {
                [pipeline: it, status: status(it), lastRun: lastRun + [result: it.enabled ? status(it) : 'SUCCESS']]
            }])
        }
        store('/api/products', summaries)
        store('/api/departments', departments.collect { Map department ->
            def own = summaries.findAll { it.departmentId == department.id }
            department + [productCount       : own.size(), serviceCount: own.sum(0) { it.serviceCount },
                          pipelineCount      : own.sum(0) { it.pipelineCount },
                          activePipelineCount: own.sum(0) { it.activePipelineCount }]
        })
        store('/api/monitoring/products', [products: overview, metricsError: null])
    }

    void serve(StubApi api) {
        responses.each { path, response -> api.respond('GET', path, response) }
    }

    private void store(String path, Object body) {
        responses[path] = json(body)
    }

    private Map service(int productId, int index) {
        def template = (product.services as List<Map>)[index % (product.services as List).size()]
        def name = "${template.name}-${index + 1}".toString()
        template + [id     : (productId - 1) * SERVICES + index + 1, name: name,
                    sonar  : (template.sonar as Map) + [projectKey: "cat$productId-$name".toString()],
                    metrics: (template.metrics as Map) + [influxProject: "CAT$productId-$name".toString()]]
    }

    private Map pipelineOf(int productId, Map facts, Map service, String type, int index) {
        def id = ((service.id as int) - 1) * TYPES.size() + index + 1
        def job = "DevSecOps/${facts.code}/${service.name}-${type.toLowerCase()}".toString()
        def enabled = id % 11 != 0
        def key = activeKey(id, keyValue(id))
        newPipeline(id, facts + [id: productId], service, key, type) +
                [jenkinsJob      : job, jenkinsJobUrl: "https://jenkins.bbh.com/job/${job.replace('/', '/job/')}/".toString(),
                 enabled         : enabled, activeKey: enabled ? key : null,
                 influxProjectTag: "${facts.code}-${service.name}-${type.toLowerCase()}".toString()]
    }

    private static String status(Map pipeline) {
        def id = pipeline.id as int
        !pipeline.enabled ? 'DISABLED' : id % 5 == 0 ? 'FAILURE' : id % 3 == 0 ? 'UNSTABLE' : 'SUCCESS'
    }
}
