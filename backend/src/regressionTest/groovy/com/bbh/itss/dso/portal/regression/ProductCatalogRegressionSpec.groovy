package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.Json
import com.bbh.itss.dso.portal.support.PortalClient
import com.bbh.itss.dso.portal.support.PortalSpecification

import java.util.concurrent.Callable

import static com.bbh.itss.dso.portal.support.ApiJson.APP_ID
import static com.bbh.itss.dso.portal.support.ApiJson.build
import static com.bbh.itss.dso.portal.support.ApiJson.fullFlutterService
import static com.bbh.itss.dso.portal.support.ApiJson.fullMavenService
import static com.bbh.itss.dso.portal.support.ApiJson.fullOpenShiftService
import static com.bbh.itss.dso.portal.support.ApiJson.mavenService
import static com.bbh.itss.dso.portal.support.ApiJson.openShiftTarget
import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ApiJson.service
import static com.bbh.itss.dso.portal.support.Json.toJson
import static java.util.concurrent.Executors.newFixedThreadPool

class ProductCatalogRegressionSpec extends PortalSpecification {

    def "a product with its services is created, read back and found by search"() {
        given:
        def code = uniqueCode()

        when:
        def created = api.post('/api/products', product(code: code, name: "Product $code", ownerTeam: 'Payments Engineering',
                services: [service(name: 'gateway', nexusIqApplications: [[application: 'gateway', scanPatterns: ['**/*.jar']]]),
                           mavenService(name: 'ledger', build: build(tool: 'MAVEN', javaPath: null, autoSetup: true,
                                   buildPath: 'target/*.jar', command: [tasks: ['clean', 'verify']]))]))

        then:
        created.status == 201
        created.header('Location') == "$api.baseUrl/api/products/${created.json.id}"
        with(created.json) {
            it.code == code
            version == 0
            createdAt != null
            services*.name == ['gateway', 'ledger']
            services*.metrics*.influxProject == ["$code-gateway", "$code-ledger"]
            services[1].build == [tool   : 'MAVEN', sourceDir: '.', javaPath: null, autoSetup: true, buildPath: 'target/*.jar',
                                  command: [tasks: ['clean', 'verify'], flags: [], directory: null, mavenHome: null,
                                            environment: [], label: null, returnStdout: false]]
            services[1].delivery.tasks == ['deploy:deploy-file']
            services[0].goldenFix.enabled == null
            services[0].goldenFix.minThreatLevel == null
            services[0].nexusIqApplications*.stage == ['build']
        }

        and:
        api.get("/api/products/${created.json.id}").json == created.json
        api.get("/api/products?search=${code.toLowerCase()}").json*.code == [code]
        with(api.get('/api/products').json.find { it.code == code }) {
            serviceCount == 2
            pipelineCount == 2
            ownerTeam == 'Payments Engineering'
        }
    }

    def "every setting of a service is stored and returned as it was entered"() {
        given:
        def code = uniqueCode()
        def services = [fullMavenService(metrics: fullMavenService().metrics + [influxProject: "$code-ledger".toString()],
                                         sonar: fullMavenService().sonar + [projectKey: "$code-ledger".toString()],
                                         scm: fullMavenService().scm + [apiUrl    : 'https://bitbucket.bbh.com/rest/api/1.0',
                                                                        workspace : 'bbh', projectKey: 'LED',
                                                                        repoSlug  : 'ledger']),
                        fullOpenShiftService(), fullFlutterService()]

        when:
        def created = api.post('/api/products', product(code: code, name: "Product $code", services: services))

        then:
        created.status == 201
        created.json.services.size() == 3
        def sections = [services[0].keySet(), ['build', 'deployment', 'openShiftTargets'], ['build', 'deployment', 'flutter']]
        [created.json.services, services, sections].transpose().each { stored, sent, checked ->
            checked.each { section -> assert stored[section] == sent[section] }
        }

        and: 'reading the product back and saving it unchanged keeps every value'
        def read = api.get("/api/products/${created.json.id}").json
        read == created.json
        def saved = api.put("/api/products/${created.json.id}", product(code: code, name: "Product $code",
                version: read.version, services: read.services))
        saved.status == 200
        saved.json.services == read.services
    }

    def "an update changes kept services, adds new ones and removes the missing ones"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code",
                services: [service(name: 'gui'), service(name: 'api'), service(name: 'batch')]))
        def ids = created.services.collectEntries { [it.name, it.id] }
        def guiMetrics = created.services.find { it.name == 'gui' }.metrics

        when: 'gui is renamed and sent back with its metrics tags, as the portal UI does, so its history stays'
        def updated = api.put("/api/products/$created.id", product(code: code, name: "Renamed $code", version: created.version,
                services: [service(id: ids.api, name: 'api', description: 'REST API'),
                           service(name: 'worker'),
                           service(id: ids.gui, name: 'web', metrics: guiMetrics)]))

        then:
        updated.status == 200
        with(updated.json) {
            name == "Renamed $code"
            version == created.version + 1
            services*.name == ['api', 'worker', 'web']
            services*.id[0] == ids.api
            services*.id[2] == ids.gui
            services[0].description == 'REST API'
            services[2].metrics.influxProject == "$code-gui"
        }
        !updated.json.services*.id.contains(ids.batch)
    }

    def "a removed service frees its name and metrics tags for a new one"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code", services: [service(name: 'gui')]))

        when:
        def updated = api.put("/api/products/$created.id", product(code: code, name: "Product $code",
                services: [service(name: 'gui', description: 'new service with the old name')]))

        then:
        updated.status == 200
        updated.json.services[0].id != created.services[0].id
        updated.json.services[0].metrics.influxProject == "$code-gui"
    }

    def "an update based on an outdated version is refused"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code"))
        api.put("/api/products/$created.id", product(code: code, name: "First change $code", version: 0))

        when:
        def stale = api.put("/api/products/$created.id", product(code: code, name: "Second change $code", version: 0))

        then:
        stale.status == 409
        stale.json.detail.contains('changed by someone else')
        api.get("/api/products/$created.id").json.name == "First change $code"
    }

    def "a save based on a product read before another save is refused, whatever the other save changed (#change)"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code",
                services: [service(name: 'gui'), service(name: 'api')]))
        def read = api.get("/api/products/$created.id").json
        Map<String, Closure<List>> edits = [
                'a service added'          : { List services -> services + [service(name: 'worker')] },
                'a service removed'        : { List services -> services.take(1) },
                'only a service\'s settings': { List services -> [services[0] + [description: 'Edited by A'], services[1]] }]

        when:
        def first = api.put("/api/products/$created.id", product(code: code, name: "Product $code",
                version: read.version, services: edits[change](read.services)))
        def stale = api.put("/api/products/$created.id", product(code: code, name: "Renamed by B $code",
                version: read.version, services: read.services))
        def stored = api.get("/api/products/$created.id").json

        then:
        first.status == 200
        first.json.version > read.version
        stale.status == 409
        stale.json.detail.contains('changed by someone else')
        stored.version == first.json.version
        stored.name == "Product $code"
        stored.services*.name == names
        stored.services*.description == descriptions

        where:
        change                      | names                     | descriptions
        'a service added'           | ['gui', 'api', 'worker']  | [null, null, null]
        'a service removed'         | ['gui']                   | [null]
        'only a service\'s settings' | ['gui', 'api']            | ['Edited by A', null]
    }

    def "concurrent saves of the same product version let exactly one editor through"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code",
                services: [service(name: 'gui'), service(name: 'api')]))
        long id = created.id as long
        def read = api.get("/api/products/$id").json
        List<Map> requests = (1..8).collect { editor ->
            Json.parse(toJson(product(code: code, name: "Product $code", version: read.version,
                    services: [read.services[0] + [description: "Edited by $editor".toString()], read.services[1]]))) as Map
        }
        def pool = newFixedThreadPool(requests.size())

        when:
        def responses = pool.invokeAll(requests.collect { request ->
            { -> api.put("/api/products/$id", request) } as Callable
        })*.get()
        def stored = api.get("/api/products/$id").json
        def accepted = responses.findAll { it.status == 200 }

        then:
        accepted.size() == 1
        responses.count { it.status == 409 } == requests.size() - 1
        stored.version == accepted[0].json.version
        stored.version > read.version
        stored.services[0].description == accepted[0].json.services[0].description

        cleanup:
        pool.shutdownNow()
    }

    def "a value moves between the services of a product in one save"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code", services: [
                service(name: 'gui', sonar: [projectKey: "$code-gui".toString(), command: [tasks: ['sonar']]],
                        metrics: [influxProject: "$code-gui".toString(), influxEnv: 'uat']),
                service(name: 'api', sonar: [projectKey: "$code-api".toString(), command: [tasks: ['sonar']]],
                        metrics: [influxProject: "$code-api".toString(), influxEnv: 'uat'])]))
        def (gui, rest) = created.services

        when: 'gui and api swap their names, SonarQube keys and metrics tags, and a new service takes the old gui name'
        def swapped = api.put("/api/products/$created.id", product(code: code, name: "Product $code",
                version: created.version, services: [
                gui + [name: 'api', sonar: gui.sonar + [projectKey: rest.sonar.projectKey], metrics: rest.metrics],
                rest + [name: 'gui-legacy', sonar: rest.sonar + [projectKey: gui.sonar.projectKey], metrics: gui.metrics],
                service(name: 'gui')]))

        then:
        swapped.status == 200
        swapped.json.services*.id == [gui.id, rest.id, swapped.json.services[2].id]
        swapped.json.services*.name == ['api', 'gui-legacy', 'gui']
        swapped.json.services*.sonar*.projectKey == ["$code-api".toString(), "$code-gui".toString(), null]
        swapped.json.services[0].metrics == rest.metrics
        swapped.json.services[1].metrics == gui.metrics
        api.get("/api/products/$created.id").json == swapped.json
    }

    def "product codes and names are unique regardless of case"() {
        given:
        def code = uniqueCode()
        createProduct(product(code: code, name: "Product $code"))

        expect:
        api.post('/api/products', product(code: code, name: "Other $code")).status == 409
        api.post('/api/products', product(code: uniqueCode(), name: "product ${code.toLowerCase()}")).status == 409
    }

    def "a new product's code is suggested from its name and never repeats an existing one"() {
        given:
        def name = "Code Hub ${uniqueCode()}"
        def first = api.get("/api/products/code-suggestion?name=${URLEncoder.encode(name, 'UTF-8')}").json.code
        createProduct(product(code: first, name: name))

        expect:
        first == name.toUpperCase().replaceAll(/[^A-Z0-9]/, '')
        api.get("/api/products/code-suggestion?name=${URLEncoder.encode(name.toLowerCase(), 'UTF-8')}").json.code == "${first}2"
    }

    def "every rule a service breaks is reported against its field"() {
        given:
        def code = uniqueCode()
        def other = createProduct(product(code: uniqueCode(), name: "Other ${uniqueCode()}",
                services: [service(name: 'gui', sonar: [projectKey: "sonar-$code", command: [tasks: ['sonarqube']]])]))

        when:
        def response = api.post('/api/products', product(code: code, name: "Product $code", services: [
                service(name: 'gui', build: [tool: 'GRADLE']),
                service(name: 'api', deployment: [target: 'OPENSHIFT'], sonar: [projectKey: "sonar-$code"]),
                mavenService(name: 'batch', delivery: null,
                        appScan: [applicationId: APP_ID, dastEnabled: true],
                        scm: [repositoryUrl: 'https://bitbucket.bbh.com/projects/X/repos/batch'],
                        testJobs: [[stage: 'REGRESSION', type: 'REMOTE', job: 'batch/regression']]),
                service(name: 'gui', metrics: [influxProject: other.services[0].metrics.influxProject])]))

        then:
        response.status == 400
        response.json.errors*.field == ['services[0].build.javaPath', 'services[0].build.command.tasks',
                                        'services[1].deployment.appName', 'services[1].deployment.artifactName',
                                        'services[1].openShiftTargets[RD].projectBuild',
                                        'services[1].openShiftTargets[RD].buildConfigPath',
                                        'services[1].openShiftTargets[RD].dockerFilePath',
                                        'services[1].openShiftTargets[RD].buildContext',
                                        'services[1].openShiftTargets[RD].dockerRepoPush',
                                        'services[1].openShiftTargets[RD].nexusAuthFile',
                                        'services[1].sonar.command.tasks',
                                        'services[2].appScan.dastTargetUrl', 'services[2].scm.credentialsId',
                                        'services[2].delivery.tasks', 'services[2].testJobs[0].remoteJenkins',
                                        'services[3].name']
        api.get("/api/products?search=$code").json == []
    }

    def "texts that fit their column in characters but not in UTF-8 bytes are refused against their fields"() {
        given:
        def code = uniqueCode()

        when:
        def described = api.post('/api/products', product(code: code, name: "Product $code",
                description: 'Zażółć gęślą jaźń – ' * 160, services: [service(name: 'gui')]))
        def serviced = api.post('/api/products', product(code: code, name: "Product $code",
                services: [service(name: 'gui', description: '„Usługa” – ' * 150)]))

        then:
        [described, serviced]*.status == [400, 400]
        described.json.errors == [[field: 'description', message: 'is too long: it may take at most 4000 bytes']]
        serviced.json.errors == [[field: 'services[0].description', message: 'is too long: it may take at most 2000 bytes']]
        api.get("/api/products?search=$code").json == []
    }

    def "a service the library could not build or deploy is refused field by field (#rule)"() {
        given:
        def code = uniqueCode()

        when:
        def response = api.post('/api/products', product(code: code, name: "Product $code", services: [submitted]))

        then:
        response.status == 400
        response.json.errors*.field == fields
        api.get("/api/products?search=$code").json == []

        where:
        rule                         | submitted                                                                                || fields
        'Flutter needs its JDK'      | fullFlutterService(build: fullFlutterService().build + [javaPath: null, autoSetup: true]) || ['services[0].build.javaPath', 'services[0].build.autoSetup']
        'Flutter needs its modules'  | fullFlutterService(flutter: fullFlutterService().flutter + [modules: [], testModules: [], deliveryGroup: null]) || ['services[0].flutter.modules', 'services[0].flutter.testModules', 'services[0].flutter.deliveryGroup']
        'Nexus IQ needs both'        | service(nexusIqApplications: [[scanPatterns: ['**/*.war']]])                             || ['services[0].nexusIqApplications[0].application']
        'Maven on VMs needs a path'  | mavenService(build: build(tool: 'MAVEN', command: [tasks: ['verify']]))                  || ['services[0].build.buildPath']
        'OpenShift needs RD'         | fullOpenShiftService(openShiftTargets: [RD: openShiftTarget('x') + [nexusAuthFile: null, buildContext: ' ']]) || ['services[0].openShiftTargets[RD].buildContext', 'services[0].openShiftTargets[RD].nexusAuthFile']
        'UrbanCode needs components' | service(urbanCodeApplications: [[applicationName: 'LEDGER', components: []], [applicationName: 'BATCH', components: [[componentName: 'batch']]]]) || ['services[0].urbanCodeApplications[0].components', 'services[0].urbanCodeApplications[1].components[0].baseDir', 'services[0].urbanCodeApplications[1].components[0].fileIncludePatterns']
        'test job parameters'        | service(testJobs: [[stage: 'SMOKE', job: 'smoke', parameters: 'ENV=rd\nSUITE critical']])  || ['services[0].testJobs[0].parameters']
        'lists fit their columns'    | service(build: build(command: [tasks: ['build'], flags: (1..12).collect { "-Dp$it=${'v' * 200}".toString() }]), appScan: [applicationId: APP_ID, includedDirs: (1..20).collect { "${'d' * 150}/$it".toString() }]) || ['services[0].build.command.flags', 'services[0].appScan.includedDirs']
    }

    def "test job parameters are stored one NAME=value per line as they were entered"() {
        given:
        def code = uniqueCode()
        def parameters = 'TARGET_ENV=uat\nSUITE=critical\nlog.level=debug'

        when:
        def created = createProduct(product(code: code, name: "Product $code",
                services: [service(testJobs: [[stage: 'REGRESSION', job: 'ledger/regression', parameters: parameters]])]))
        def pipeline = pipelineFor(created.services[0].id as long)
        def config = api.get("/api/dso/config/$pipeline.activeKey.value?format=json").json

        then:
        created.services[0].testJobs[0].parameters == parameters
        api.get("/api/products/$created.id").json.services[0].testJobs[0].parameters == parameters
        config.projects.gui.tests.regression.jobs[0].parameters == parameters
    }

    def "malformed input is refused before it reaches the business rules"() {
        expect:
        api.post('/api/products', body).status == 400

        where:
        body << [product(code: 'lower-case'),
                 product(services: [service(build: [tool: 'ANT'])]),
                 product(services: [service(appScan: [applicationId: 'not-a-uuid'])]),
                 product(services: [service(testJobs: [[stage: 'SMOKE', job: ' ']])]),
                 product(services: [service(sshTargets: [RD: [host: 'not a host']])]),
                 product(services: [service(goldenFix: [ecosystems: ['gradle']])]),
                 product(services: [service(urbanCodeApplications: [[applicationName: 'LEDGER', components: [null]]])]),
                 product(services: [service(build: build(command: [tasks: ['clean'], environment: ['not a variable']]))]),
                 [code: 'X']]
    }

    def "a product needs its AppScan key once it has services, and not before"() {
        given:
        def code = uniqueCode()

        when:
        def created = api.post('/api/products', product(code: code, name: "Product $code", appScan: null, services: []))
        def id = created.json.id
        def withoutKey = api.put("/api/products/$id", product(code: code, name: "Product $code", version: 0,
                appScan: [keyId: ' ', secretCredentialsId: null], services: [service(name: 'gui')]))

        then:
        created.status == 201
        created.json.appScan == null
        created.json.services == []
        api.get("/api/products/$id/pipelines").json == []
        withoutKey.status == 400
        withoutKey.json.errors == [[field: 'appScan.keyId', message: 'must not be blank']]
        api.post('/api/products', product(code: uniqueCode(), name: "Other $code", appScan: null)).json.errors ==
                [[field: 'appScan.keyId', message: 'must not be blank']]

        when:
        def withKey = api.put("/api/products/$id", product(code: code, name: "Product $code", version: 0,
                services: [service(name: 'gui')]))

        then:
        withKey.status == 200
        withKey.json.appScan == [keyId: 'bbh_key-id', secretCredentialsId: 'hcl-app-scan-account']
        api.get("/api/products/$id/pipelines").json*.pipelines*.type == [['FULL']]
    }

    def "every service a save creates starts with a full pipeline named by the service template and a key that reads its configuration"() {
        given:
        def code = uniqueCode()

        when:
        def created = createProduct(product(code: code, name: "Product $code",
                services: [service(name: 'gui'), service(name: 'backend-api')]))
        def started = api.get("/api/products/$created.id/pipelines").json

        then:
        started*.serviceName == ['gui', 'backend-api']
        started.every { it.pipelines*.type == ['FULL'] }
        started.every { it.pipelines[0].agentLabels == ['linux-agent'] }
        started*.pipelines*.jenkinsJob == [["DevSecOps/$code/gui-full"], ["DevSecOps/$code/backend-api-full"]]
        started.every { it.pipelines[0].enabled && it.pipelines[0].activeKey.status == 'ACTIVE' }
        started.every { api.get("/api/dso/config/${it.pipelines[0].activeKey.value}").status == 200 }

        when:
        def kept = created.services[0]
        def updated = api.put("/api/products/$created.id", product(code: code, name: "Product $code",
                services: [service(id: kept.id, name: 'gui'), service(name: 'worker')])).json
        def afterAdding = api.get("/api/products/$created.id/pipelines").json

        then:
        afterAdding*.serviceName == ['gui', 'worker']
        afterAdding.every { it.pipelines*.type == ['FULL'] }
        afterAdding[0].pipelines[0].id == started[0].pipelines[0].id
        afterAdding[0].pipelines[0].activeKey.value == started[0].pipelines[0].activeKey.value
        afterAdding[1].pipelines[0].activeKey.value != started[1].pipelines[0].activeKey.value

        when:
        api.delete("/api/pipelines/${afterAdding[1].pipelines[0].id}")
        api.put("/api/products/$created.id", product(code: code, name: "Product $code 2",
                services: [service(id: kept.id, name: 'gui'), service(id: updated.services[1].id, name: 'worker')]))

        then:
        api.get("/api/products/$created.id/pipelines").json[1].pipelines == []
    }

    def "a save naming a pipeline type gives every service of the product a pipeline of that type"() {
        given:
        def code = uniqueCode()

        when:
        def created = api.post('/api/products?pipelineType=SAST', product(code: code, name: "Product $code",
                services: [service(name: 'gui')]))

        then:
        created.status == 201
        api.get("/api/products/$created.json.id/pipelines").json*.pipelines*.type == [['SAST']]

        when:
        def updated = api.put("/api/products/$created.json.id?pipelineType=SECURITY", product(code: code,
                name: "Product $code", services: [service(id: created.json.services[0].id, name: 'gui'),
                                                  service(name: 'worker')]))
        def pipelines = api.get("/api/products/$created.json.id/pipelines").json

        then:
        updated.status == 200
        pipelines*.pipelines*.type == [['SAST', 'SECURITY'], ['SECURITY']]
        pipelines.every { it.pipelines.every { it.activeKey.status == 'ACTIVE' } }

        expect:
        api.post('/api/products?pipelineType=NIGHTLY', product(code: uniqueCode(), name: 'Nightly',
                services: [service(name: 'gui')])).status == 400
    }

    def "a product onboarded for Nexus IQ GoldenFix gives each service a pipeline whose key reads the nexusiq config"() {
        given:
        def code = uniqueCode('NIQ')
        def repository = 'https://bitbucket.bbh.com/projects/NIQ/repos/gui'

        when:
        def created = api.post('/api/products?pipelineType=NEXUS_IQ', product(code: code, name: "Product $code",
                services: [service(name: 'gui', nexusIqApplications: [[application: 'niq-gui',
                                                                       scanPatterns: ['**/build/libs/*.jar']]],
                                   scm: [repositoryUrl: repository, credentialsId: 'bitbucket-http-credentials']),
                           service(name: 'worker')]))
        def pipelines = api.get("/api/products/$created.json.id/pipelines").json*.pipelines
        def gui = pipelines[0][0]
        def config = api.get("/api/dso/config/$gui.activeKey.value?format=json").json

        then:
        created.status == 201
        pipelines*.type == [['NEXUS_IQ'], ['NEXUS_IQ']]
        gui.entryPoint == 'devSecOpsNexusIqGoldenFixPipeline'
        gui.activeKey.status == 'ACTIVE'
        config.pipeline.subMap(['type', 'entryPoint', 'product', 'projectNames']) ==
                [type: 'nexusiq', entryPoint: 'devSecOpsNexusIqGoldenFixPipeline', product: code, projectNames: 'gui']
        config.projects.gui.tools.nexusIq.application == 'niq-gui'
        config.projects.gui.scm.bitbucket.url == repository
        config.projects.gui.influx.project == "$code-gui".toString()

        when:
        def updated = api.put("/api/products/$created.json.id?pipelineType=SAST", product(code: code,
                name: "Product $code", services: created.json.services))

        then:
        updated.status == 200
        api.get("/api/products/$created.json.id/pipelines").json*.pipelines*.type ==
                [['NEXUS_IQ', 'SAST'], ['NEXUS_IQ', 'SAST']]
    }

    def "#refusal is a problem detail that keeps the portal's internals to itself"() {
        when:
        def response = call.call(api)

        then:
        response.status == status
        response.header('Content-Type').startsWith('application/problem+json')
        with(response.json) {
            !it.title.isBlank()
            !it.detail.isBlank()
            def text = "$it.title $it.detail"
            !text.contains('com.bbh.itss') && !text.contains('java.') && !text.contains('Exception') &&
                    !text.contains('(') && !text.contains('$')
        }

        where:
        refusal                        | status | call
        'an unknown endpoint'          | 404    | { PortalClient http -> http.get('/api/nowhere') }
        'a method the endpoint lacks'  | 405    | { PortalClient http -> http.delete('/api/products') }
        'a path value of a wrong type' | 400    | { PortalClient http -> http.get('/api/products/undefined') }
        'a body that is not JSON'      | 400    | { PortalClient http ->
            http.postRaw('/api/products', 'application/json', '{"code": ')
        }
        'a body of another type'       | 415    | { PortalClient http ->
            http.postRaw('/api/products', 'text/plain', 'code=CERT')
        }
        'a request without a body'     | 400    | { PortalClient http ->
            http.postRaw('/api/products', 'application/json', '')
        }
        'a body that is not an object' | 400    | { PortalClient http ->
            http.postRaw('/api/products', 'application/json', '["CERT"]')
        }
    }

    def "a value the request body cannot hold is refused against the field that holds it"() {
        when:
        def unknownTool = api.post('/api/products', product(services: [service(build: build(tool: 'ANT'))]))
        def wrongType = api.post('/api/products',
                product(services: [service(build: build(command: [tasks: 'clean build']))]))
        def missing = api.postRaw('/api/products', 'application/json', '')

        then:
        unknownTool.status == 400
        with(unknownTool.json) {
            title == 'Malformed request'
            detail == 'services[0].build.tool must be one of GRADLE, MAVEN, FLUTTER'
            errors == [[field: 'services[0].build.tool', message: 'must be one of GRADLE, MAVEN, FLUTTER']]
        }
        wrongType.status == 400
        wrongType.json.errors == [[field  : 'services[0].build.command.tasks',
                                   message: 'has a value this field cannot hold']]
        missing.json.detail == 'The request body is missing.'
        missing.json.errors == null
    }

    def "a deleted product is gone together with its pipelines and keys"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code"))
        def pipeline = pipelineFor(created.services[0].id)

        when:
        def deleted = api.delete("/api/products/$created.id")

        then:
        deleted.status == 204
        api.get("/api/products/$created.id").status == 404
        api.get("/api/pipelines/$pipeline.id").status == 404
        api.get("/api/dso/config/$pipeline.activeKey.value").status == 404
    }

    private List<Map> pipelinesOf(long productId) {
        api.get("/api/products/$productId/pipelines").json*.pipelines.flatten() as List<Map>
    }
}
