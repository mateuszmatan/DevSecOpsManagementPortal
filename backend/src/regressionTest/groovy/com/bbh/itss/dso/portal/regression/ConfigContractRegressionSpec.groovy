package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.PortalSpecification

import static com.bbh.itss.dso.portal.support.ApiJson.build
import static com.bbh.itss.dso.portal.support.ApiJson.fullFlutterService
import static com.bbh.itss.dso.portal.support.ApiJson.fullMavenService
import static com.bbh.itss.dso.portal.support.ApiJson.fullOpenShiftService
import static com.bbh.itss.dso.portal.support.ApiJson.openShiftTarget
import static com.bbh.itss.dso.portal.support.ApiJson.pipeline
import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ApiJson.service

class ConfigContractRegressionSpec extends PortalSpecification {

    static final String EXPECTED_DIR = System.getProperty('regression.expectedDir', 'src/regressionTest/resources/regression')

    static final Map PAYMENTS = product(code: 'CONTRACT', name: 'Contract Payments Hub', description: 'Payment orchestration',
            ownerTeam: 'Payments Engineering', contactEmail: 'payments-eng@bbh.com',
            appScan: [keyId: 'bbh_1c2d3e4f-0000-4abc-9def-123456789abc', secretCredentialsId: 'hcl-app-scan-account'],
            services: [
                    fullOpenShiftService(name: 'gateway', description: 'Public payment API',
                            build: build(tool: 'MAVEN', sourceDir: 'gateway', command: [tasks: ['clean', 'verify'], flags: ['-B']]),
                            deployment: [target: 'OPENSHIFT', appName: 'payhub-gateway', artifactName: 'payhub-gateway.jar'],
                            openShiftTargets: [RD: openShiftTarget('payhub-rd') + [buildTag: '2.3.1',
                                                       internalDockerUrl: 'image-registry.openshift-image-registry.svc:5000/payhub-rd'],
                                               QC: openShiftTarget('payhub-qc') + [buildTag: '2.3.0']],
                            appScan: [applicationId: '3a1b2c3d-1111-4a5b-8c9d-0e1f2a3b4c5d', sastScanName: 'payhub-gateway',
                                      dastEnabled: true, dastTargetUrl: 'https://payhub-uat.testbbh.com', dastPresenceId: 'presence-7'],
                            sonar: [projectName: 'PayHub Gateway', projectKey: 'payhub-gateway', command: [tasks: ['sonar:sonar']]],
                            nexusIqApplications: [[application: 'payhub-gateway', scanPatterns: ['**/target/*.jar', '**/target/*.war']],
                                                  [application: 'payhub-gateway-batch', scanPatterns: ['**/batch/*.jar'],
                                                   stage: 'release', failOnNetworkError: true]],
                            scm: [repositoryUrl: 'https://bitbucket.bbh.com/projects/PAY/repos/payhub-gateway',
                                  credentialsId: 'bitbucket-http-credentials'],
                            metrics: [influxProject: 'payhub-gateway', influxEnv: 'uat'],
                            testJobs: [[stage: 'REGRESSION', name: 'PayHub regression', type: 'LOCAL',
                                        job: 'payhub/regression-tests', timeoutMinutes: 60]]),
                    fullMavenService(),
                    fullFlutterService(name: 'mobile-app', description: 'Flutter mobile application',
                            goldenFix: [enabled: false])])

    def "the pipeline and product configs keep the shape the DevSecOps library reads"() {
        given:
        def payments = createProduct(PAYMENTS)
        def security = pipelineFor(payments.services[0].id as long, pipeline(type: 'SECURITY',
                agentLabels: ['linux-agent', 'docker'], extendedPipelineJob: 'PAYHUB/gateway-extended'))
        def nexusIq = pipelineFor(payments.services[0].id as long, pipeline(type: 'NEXUS_IQ',
                extendedPipelineJob: 'PAYHUB/gateway-extended', securityPipelineJob: 'PAYHUB/gateway-security'))

        when:
        def pipelineConfig = api.get("/api/dso/config/$security.activeKey.value")
        def nexusIqConfig = api.get("/api/dso/config/$nexusIq.activeKey.value")
        def productConfig = api.get("/api/products/$payments.id/config")

        then:
        pipelineConfig.status == 200
        nexusIqConfig.status == 200
        productConfig.status == 200
        matchesExpected('pipeline-config.yaml', pipelineConfig.body)
        matchesExpected('nexus-iq-pipeline-config.yaml', nexusIqConfig.body)
        matchesExpected('product-config.yaml', productConfig.body)
    }

    def "the Bitbucket repository keys GoldenFix pull requests use are rendered only when they are set"() {
        given:
        def created = createProduct(product(code: uniqueCode('BITBUCKET'), name: "Bitbucket Hub ${uniqueCode()}",
                services: [service(name: 'server', scm: [repositoryUrl: 'https://bitbucket.bbh.com/scm/pay/payhub.git',
                                                         credentialsId: 'bitbucket-http-credentials', type: 'SERVER',
                                                         apiUrl       : 'https://bitbucket.bbh.com/rest/api/1.0',
                                                         projectKey   : 'PAY', repoSlug: 'payhub']),
                           service(name: 'cloud', scm: [repositoryUrl: 'https://bitbucket.org/bbh/payhub-mobile',
                                                        credentialsId: 'bitbucket-cloud-token', authType: 'BEARER',
                                                        type         : 'CLOUD', workspace: 'bbh',
                                                        repoSlug     : 'payhub-mobile']),
                           service(name: 'plain', scm: [repositoryUrl: 'https://bitbucket.bbh.com/projects/PAY/repos/plain',
                                                        credentialsId: 'bitbucket-http-credentials'])]))
        def server = pipelineFor(created.services[0].id as long, pipeline(type: 'FULL'))

        when:
        def projects = api.get("/api/products/$created.id/config?format=json").json.projects
        def library = api.get("/api/dso/config/$server.activeKey.value?format=json").json

        then:
        created.services*.scm*.subMap(['apiUrl', 'workspace', 'projectKey', 'repoSlug']) == [
                [apiUrl: 'https://bitbucket.bbh.com/rest/api/1.0', workspace: null, projectKey: 'PAY', repoSlug: 'payhub'],
                [apiUrl: null, workspace: 'bbh', projectKey: null, repoSlug: 'payhub-mobile'],
                [apiUrl: null, workspace: null, projectKey: null, repoSlug: null]]
        projects.server.scm.bitbucket == [url       : 'https://bitbucket.bbh.com/scm/pay/payhub.git',
                                          credentialsId: 'bitbucket-http-credentials', authType: 'basic', type: 'server',
                                          apiUrl    : 'https://bitbucket.bbh.com/rest/api/1.0', projectKey: 'PAY',
                                          repoSlug  : 'payhub']
        projects.cloud.scm.bitbucket == [url          : 'https://bitbucket.org/bbh/payhub-mobile',
                                         credentialsId: 'bitbucket-cloud-token', authType: 'bearer', type: 'cloud',
                                         workspace    : 'bbh', repoSlug: 'payhub-mobile']
        projects.plain.scm.bitbucket == [url          : 'https://bitbucket.bbh.com/projects/PAY/repos/plain',
                                         credentialsId: 'bitbucket-http-credentials', authType: 'basic']
        library.projects.keySet() == ['server'] as Set
        library.projects.server.scm == projects.server.scm
    }

    def "Bitbucket repository keys without a repository URL are refused"() {
        when:
        def response = api.post('/api/products', product(code: uniqueCode('NOREPO'), name: "No Repo ${uniqueCode()}",
                services: [service(scm: [workspace: 'bbh', repoSlug: 'payhub'])]))

        then:
        response.status == 400
        response.json.errors.collect { [it.field, it.message] } == [['services[0].scm.repositoryUrl',
                'is required when the Bitbucket API URL, workspace, project key or repository slug is set']]
    }

    def "the global part of the configuration keeps the shape of the library's defaults"() {
        when:
        def globalConfig = api.get('/api/settings/config')

        then:
        globalConfig.status == 200
        globalConfig.header('Content-Type').startsWith('application/yaml')
        matchesExpected('global-config.yaml', globalConfig.body)
    }

    def "the portal shows a pipeline the same config its key gets, without marking the key as used"() {
        given:
        def mobile = createProduct(PAYMENTS + [code: uniqueCode('PREVIEW'), name: 'Preview Payments Hub',
                                               services: [PAYMENTS.services[2]]])
        def full = pipelineFor(mobile.services[0].id as long, pipeline(type: 'FULL'))

        when:
        def preview = api.get("/api/pipelines/$full.id/config")

        then:
        preview.status == 200
        preview.header('Content-Type').startsWith('application/yaml')
        api.get("/api/pipelines/$full.id").json.activeKey.lastUsedAt == null

        and:
        preview.body == api.get("/api/dso/config/$full.activeKey.value").body
        api.get("/api/pipelines/$full.id").json.activeKey.lastUsedAt != null
    }

    def "a pipeline's key gets byte for byte the configuration the portal previews, also right after a change (#kind, #format)"() {
        given:
        String code = uniqueCode('KEYED')
        Map services = [vm       : fullMavenService(sonar: fullMavenService().sonar + [projectKey: code.toLowerCase()],
                                                    metrics: [enabled: true, influxProject: code.toLowerCase(), influxEnv: 'uat']),
                        openshift: fullOpenShiftService(),
                        flutter  : fullFlutterService()]
        def created = createProduct(product(code: code, name: "Keyed $code", services: [services[kind]]))
        def full = pipelineFor(created.services[0].id as long, pipeline(type: 'FULL'))
        String byKey = "/api/dso/config/$full.activeKey.value?format=$format"
        String preview = "/api/pipelines/$full.id/config?format=$format"

        when:
        def fetched = api.get(byKey)
        def previewed = api.get(preview)

        then:
        fetched.status == 200
        fetched.header('Content-Type') == previewed.header('Content-Type')
        fetched.body == previewed.body

        when:
        def read = api.get("/api/products/$created.id").json
        def changed = api.put("/api/products/$created.id", product(code: read.code, name: read.name,
                version: read.version, services: [read.services[0] + [build: read.services[0].build + [sourceDir: 'moved-dir']]]))
        def refetched = api.get(byKey)

        then:
        changed.status == 200
        refetched.body == api.get(preview).body
        refetched.body != fetched.body
        refetched.body.contains('moved-dir')

        where:
        [kind, format] << [['vm', 'openshift', 'flutter'], ['yaml', 'json']].combinations()
    }

    private static boolean matchesExpected(String name, String actual) {
        def file = new File(EXPECTED_DIR, name)
        if (Boolean.getBoolean('regression.updateExpected')) {
            file.parentFile.mkdirs()
            file.text = actual
        }
        assert file.exists(): "$file is missing; create it with -Dregression.updateExpected=true"
        assert actual.normalize() == file.text.normalize()
        true
    }
}
