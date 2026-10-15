package com.bbh.itss.dso.portal.frontend.support

import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture

class ApiData {

    static final String ISSUED_AT = '2026-10-05T09:00:00Z'

    static String keyValue(long serial) {
        String.format('%08x-%04x-4%03x-8%03x-%012x', (serial * 2654435761L) & 0xffffffffL, serial & 0xffff,
                serial & 0xfff, (serial * 7) & 0xfff, serial * 104729L)
    }

    static String hint(String value) {
        value.take(8) + '…' + value.drop(value.length() - 4)
    }

    static Map activeKey(long id, String value, String issuedAt = ISSUED_AT) {
        [id         : id, value: value, hint: hint(value), status: 'ACTIVE', issuedAt: issuedAt, revokedAt: null,
         revokeReason: null, lastUsedAt: null]
    }

    static Map revokedKey(Map key, String reason, String revokedAt) {
        key + [value: null, status: 'REVOKED', revokedAt: revokedAt, revokeReason: reason]
    }

    static Map pipeline(Map overrides) {
        (fixture('pipeline-1.json') as Map) + [keys: null] + overrides
    }

    static final Map<String, String> ENTRY_POINTS = [FULL    : 'devSecOpsPipeline', SECURITY: 'devSecOpsSecurityPipeline',
                                                     EXTENDED: 'devSecOpsExtendedPipeline',
                                                     SAST    : 'devSecOpsSASTScanningPipeline',
                                                     NEXUS_IQ: 'devSecOpsNexusIqGoldenFixPipeline']

    static Map newPipeline(long id, Map product, Map service, Map key, String type) {
        def code = product.code as String
        def metrics = service.metrics as Map
        [id                 : id, productId: product.id, productCode: code, productName: product.name,
         serviceId          : service.id, serviceName: service.name, type: type, entryPoint: ENTRY_POINTS[type],
         agentLabels        : ['linux-agent'], extendedPipelineJob: null, securityPipelineJob: null, jenkinsJob: null,
         jenkinsJobUrl      : null, description: null, enabled: true, activeKey: key,
         influxProjectTag   : metrics?.influxProject ?: "${code}-${service.name}".toString(),
         influxEnv          : metrics?.influxEnv ?: 'test', createdAt: key.issuedAt, updatedAt: key.issuedAt, keys: null]
    }

    static Map servicePipelines(Map service, List<Map> pipelines) {
        [serviceId  : service.id, serviceName: service.name, description: service.description,
         buildTool  : (service.build as Map).tool, deployTarget: (service.deployment as Map).target,
         pipelines  : pipelines]
    }

    static Map noFlutterSettings() {
        ((fixture('product-1.json') as Map).services as List<Map>)[0].flutter as Map
    }

    static Map withoutResponseFields(Map product) {
        def request = product.findAll { key, value -> !(key in ['id', 'createdAt', 'updatedAt']) }
        request.services = (product.services as List<Map>).collect { service ->
            service + [flutter: (service.build as Map).tool == 'FLUTTER' ? service.flutter : null]
        }
        request
    }
}
