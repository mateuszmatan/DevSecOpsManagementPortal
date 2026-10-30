package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.adapter.in.web.ConfigImportResponse.ImportedPipeline
import com.bbh.itss.dso.portal.adapter.in.web.ConfigImportResponse.ImportedService
import com.bbh.itss.dso.portal.adapter.in.web.ServiceDto.MetricsSettingsDto
import com.bbh.itss.dso.portal.adapter.in.web.ServiceDto.NexusIqApplicationDto
import com.bbh.itss.dso.portal.adapter.in.web.ServiceDto.NodeBuildSettingsDto
import com.bbh.itss.dso.portal.adapter.in.web.ServiceDto.SshTargetDto
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings
import com.bbh.itss.dso.portal.domain.dsoconfig.DsoConfigBuilder
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import org.yaml.snakeyaml.Yaml
import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import static com.bbh.itss.dso.portal.adapter.in.web.ConfigFileParser.LIMITS
import static com.bbh.itss.dso.portal.adapter.in.web.ConfigFileParser.MAX_LENGTH
import static com.bbh.itss.dso.portal.adapter.in.web.ConfigFileParser.SELF_REFERENCE
import static com.bbh.itss.dso.portal.adapter.in.web.ConfigInput.SECRET
import static com.bbh.itss.dso.portal.adapter.in.web.ConfigInput.UNKNOWN
import static com.bbh.itss.dso.portal.adapter.in.web.ServiceConfigReader.GLOBAL
import static com.bbh.itss.dso.portal.adapter.in.web.ServiceConfigReader.MAX_PROJECTS
import static com.bbh.itss.dso.portal.adapter.in.web.ServiceConfigReader.NODE_WITH_FLUTTER
import static com.bbh.itss.dso.portal.adapter.in.web.ServiceConfigReader.NO_PROJECT
import static com.bbh.itss.dso.portal.adapter.in.web.ServiceConfigReader.PIPELINE
import static com.bbh.itss.dso.portal.adapter.in.web.ServiceConfigReader.RUN_TIME
import static com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType.BASIC
import static com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType.BEARER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM
import static com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings.ONE_APPLICATION
import static com.bbh.itss.dso.portal.domain.catalog.Region.QC
import static com.bbh.itss.dso.portal.domain.catalog.Region.RD
import static com.bbh.itss.dso.portal.domain.catalog.TestJobType.LOCAL
import static com.bbh.itss.dso.portal.domain.catalog.TestJobType.REMOTE
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.PERFORMANCE
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.REGRESSION
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.SMOKE
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.EXTENDED
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY
import static com.bbh.itss.dso.portal.support.CatalogFixtures.copy
import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.Fixtures.deployment
import static com.bbh.itss.dso.portal.support.Fixtures.fullCommand
import static com.bbh.itss.dso.portal.support.Fixtures.fullSettings
import static com.bbh.itss.dso.portal.support.Fixtures.globalSettings
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.settings
import static com.bbh.itss.dso.portal.support.LibraryConfig.projectsAsRun

class ServiceConfigReaderSpec extends Specification {

    static final ServiceSettings MAVEN_VM = fullSettings()
    static final ServiceSettings GRADLE_OPENSHIFT = gradleOpenShift(false)
    static final ServiceSettings GRADLE_OPENSHIFT_NO_DELIVERY = gradleOpenShift(true)
    static final ServiceSettings FLUTTER_VM = settings(build: build(tool: FLUTTER), flutter: MAVEN_VM.flutter(),
            tests: MAVEN_VM.tests(), testJobs: MAVEN_VM.testJobs(), urbanCode: MAVEN_VM.urbanCode(),
            urbanCodeApplications: MAVEN_VM.urbanCodeApplications(), sshTargets: MAVEN_VM.sshTargets(),
            appScan: MAVEN_VM.appScan(), sonar: MAVEN_VM.sonar(), scm: MAVEN_VM.scm(), metrics: MAVEN_VM.metrics())

    @Shared
    JsonMapper json = JsonMapper.builder().build()

    GlobalSettingsValues global = globalSettings()
    DsoConfigBuilder builder = new DsoConfigBuilder(global)
    ServiceConfigReader reader = new ServiceConfigReader(global, json)

    def "a #tool service on #target (delivery disabled: #disabled) read back from its config, in YAML or JSON, runs the same"() {
        given:
        Map config = builder.productConfig(product(services: [[name: 'gui', settings: settings]]))

        when:
        def fromYaml = imported(new Yaml().dump(config))
        def fromJson = imported(json.writeValueAsString(config))

        then:
        [fromYaml, fromJson].every { it.services()*.name() == ['gui'] && it.pipeline() == null && it.notes() == [] }
        asRun(rendered(fromYaml.services()[0])) == asRun(config)
        asRun(rendered(fromJson.services()[0])) == asRun(config)
        fromYaml.services()[0].notes() == notes
        fromJson.services()[0] == fromYaml.services()[0]

        where:
        settings                     || notes
        MAVEN_VM                     || []
        GRADLE_OPENSHIFT             || [problem('deploy.openshift.rd.buildTag', RUN_TIME),
                                         problem('deploy.openshift.rd.internalDockerUrl', RUN_TIME)]
        GRADLE_OPENSHIFT_NO_DELIVERY || []
        FLUTTER_VM                   || []

        tool = settings.build().tool()
        target = settings.deployment().target()
        disabled = settings.deployment().nexusDeliveryDisabled()
    }

    def "values equal to the Library defaults come back empty, so the service keeps inheriting them"() {
        when:
        def service = imported('''
            appId: 109f44ac-cc06-4ca0-884e-d944904f7019
            tests:
              maxParallel: 20
            goldenFix:
              enabled: true
              onlyDirectDependencies: true
              minThreatLevel: 7
              verify:
                maxAttempts: 3
                timeoutMinutes: 45
            '''.stripIndent()).services()[0]

        then:
        service.notes() == []
        service.service().tests().maxParallel() == null
        with(service.service().goldenFix()) {
            [enabled(), onlyDirectDependencies(), verifyMaxAttempts()] == [null, null, null]
            [minThreatLevel(), verifyTimeoutMinutes()] == [7, 45]
        }
    }

    def "Node.js build values equal to the Library defaults or to the library's own defaults come back empty"() {
        given:
        global = globalSettings { copy(it, serviceDefaults: copy(it.serviceDefaults(), nodeInstallationName: 'NodeJS-20',
                npmRegistry: 'https://nexus.bbh.com/repository/npm-group/')) }
        reader = new ServiceConfigReader(global, json)

        when:
        def service = imported('''
            appId: 109f44ac-cc06-4ca0-884e-d944904f7019
            build:
              node:
                enabled: true
                commands: [npm ci, npm run build]
                installationName: NodeJS-20
                registry: https://nexus.bbh.com/repository/npm-group/
                npmrcCredentialsId: cert-npmrc
                timeoutMin: 30
                nexusIqScan: true
            '''.stripIndent()).services()[0]

        then:
        service.notes() == []
        service.service().nodeBuild() ==
                new NodeBuildSettingsDto(true, null, [], null, null, [], null, 'cert-npmrc', null, null)
    }

    def "Node.js build keys without enabled: true come back as a build that is switched off and keeps its values"() {
        when:
        def service = imported('''
            appId: 109f44ac-cc06-4ca0-884e-d944904f7019
            build:
              node:
                dir: frontend
                commands: npm run build:prod
                env:
                  CI: true
                timeoutMin: 45
                nexusIqScan: false
            goldenFix:
              versionCatalogs: [gradle/libs.versions.toml, platform/pom.xml]
            '''.stripIndent()).services()[0]

        then:
        service.notes() == []
        service.service().nodeBuild() ==
                new NodeBuildSettingsDto(false, 'frontend', ['npm run build:prod'], null, null, ['CI=true'], null, null, 45, false)
        service.service().versionCatalogs() == ['gradle/libs.versions.toml', 'platform/pom.xml']
        service.service().goldenFix().enabled() == null
    }

    def "a Flutter config that enables the Node.js build imports it switched off with a note"() {
        when:
        def service = imported('''
            appId: 109f44ac-cc06-4ca0-884e-d944904f7019
            buildTool: flutter
            build:
              node:
                enabled: true
                dir: web
            '''.stripIndent()).services()[0]

        then:
        service.notes() == [problem('build.node.enabled', NODE_WITH_FLUTTER)]
        service.service().nodeBuild().enabled() == false
        service.service().nodeBuild().directory() == 'web'
    }

    def "the delivery switch is read from the service's own deploy target and the other target's is left out"() {
        when:
        def service = imported('''
            appId: 109f44ac-cc06-4ca0-884e-d944904f7019
            deployTarget: vm
            deploy:
              vm:
                rd:
                  host: rdltaapps9.testbbh.com
              openshift:
                rd:
                  nexusDeliveryEnabled: false
            '''.stripIndent()).services()[0]

        then:
        !service.service().deployment().nexusDeliveryDisabled()
        service.notes() == [problem('deploy.openshift.rd.nexusDeliveryEnabled', 'not imported: the service deploys to vm')]
    }

    def "a hand-written config.yaml gives a service per project, inherits the global values and notes what it leaves out"() {
        when:
        def result = imported(getClass().getResource('/config-import/CertScanner-config.yaml').text)
        def gui = result.services()[0].service()
        def api = result.services()[1].service()

        then:
        result.services()*.name() == ['gui', 'backend-api']
        result.pipeline() == null
        result.notes() == []
        result.services()[0].notes() == [problem('jenkins.pipeline.extendedPipeline', PIPELINE),
                                         problem('asoc.keySecret', SECRET), problem('influx.token', SECRET),
                                         problem('sca.scanName', UNKNOWN), problem('dast.scanName', UNKNOWN)]
        result.services()[1].notes() == [problem('asoc.keySecret', SECRET), problem('influx.token', SECRET),
                                         problem('sca.scanName', UNKNOWN), problem('dast.scanName', UNKNOWN),
                                         problem('build.maven.javaPath', UNKNOWN)]
        !json.writeValueAsString(result).contains('O4c95brMewp')

        with(gui) {
            [build.tool(), build.sourceDir(), build.javaPath()] == [GRADLE, '.', '/usr/lib/jvm/java-17-openjdk']
            [build.command().tasks(), build.command().flags()] == [['clean', 'build', 'bootJar'], ['--refresh-dependencies']]
            [appScan.applicationId(), appScan.keyId(), appScan.secretCredentialsId(), appScan.scanName()] ==
                    ['209f44ac-dd06-4ca0-884e-d944904f8020', 'bbh_b81fbc9f-39c1-8eb4-38b5-b702268969b9', null,
                     'CertScanner-GUI-SAST']
            [appScan.dastEnabled(), appScan.dastTargetUrl(), appScan.dastPresenceId()] ==
                    [true, 'http://rdltaapps1.testbbh.com', null]
            metrics == new MetricsSettingsDto(true, 'CertScanner', 'test', null, null)
            [sonar.serverUrl(), sonar.installationName(), sonar.credentialsId(), sonar.authTokenCredentialsId()] ==
                    [null, null, 'sonarqube-token', 'sonarqube-token']
            [sonar.addBadges(), sonar.fullBadges(), sonar.command().tasks()] == [true, true, ['sonarqube']]
            [nexusIq.serverUrl(), nexusIq.credentialsId()] == [null, null]
            nexusIqApplications == [new NexusIqApplicationDto('CertValidityMonitoring-GUI', ['**/gui/build/libs/*.jar'],
                    'build', false)]
            [scm.repositoryUrl(), scm.authType(), scm.targetBranch(), scm.reviewers()] ==
                    ['https://bitbucket.bbh.com/projects/TA/repos/cert-scanner', BASIC, null, []]
            goldenFix.enabled() == null
            [unitTests.command().tasks(), unitTests.resultPattern()] ==
                    [['test', 'jacocoTestReport', 'jacocoTestCoverageVerification'], 'build/test-results/test/*.xml']
            [tests.regressionMaxParallel(), tests.smokeMaxParallel()] == [5, 20]
            testJobs.collect { [it.stage(), it.name(), it.type(), it.job(), it.timeoutMinutes(), it.credentialsId()] } == [
                    [SMOKE, 'CertScanner-GUI - login smoke', REMOTE,
                     'https://jenkins-a.bbh.com/job/smoke/job/cert-scanner-login', 15, 'remote-jenkins-api-token'],
                    [SMOKE, 'CertScanner-GUI - certificate list smoke', REMOTE,
                     'https://jenkins-a.bbh.com/job/smoke/job/cert-scanner-list', 15, 'remote-jenkins-api-token'],
                    [SMOKE, 'CertScanner-GUI - notification smoke', REMOTE, 'smoke/cert-scanner-notifications', 15,
                     'remote-jenkins-api-token'],
                    [SMOKE, 'CertScanner-GUI - local smoke', LOCAL, 'cert-scanner/smoke-tests', 15,
                     'remote-jenkins-api-token'],
                    [SMOKE, null, REMOTE, 'https://jenkins-c.bbh.com/job/smoke/job/cert-scanner-dashboard', 15,
                     'remote-jenkins-api-token'],
                    [SMOKE, null, REMOTE, 'https://jenkins-b.bbh.com/job/smoke/job/cert-scanner-expiry', 15,
                     'remote-jenkins-api-token'],
                    [REGRESSION, 'CertScanner-GUI - regression', LOCAL, 'cert-scanner/regression-tests', 60, null],
                    [REGRESSION, 'CertScanner-GUI - regression (certificates)', LOCAL,
                     'cert-scanner/regression-certificates', 60, null],
                    [PERFORMANCE, 'CertScanner-GUI - performance', LOCAL, 'cert-scanner/performance-tests', 120, null]]
            testJobs[2].remoteJenkins() == 'jenkins-b'
            testJobs[2].parameters() == 'ENV=rd'
            [urbanCode.siteName(), urbanCode.deployProcess()] == [null, null]
            urbanCodeApplications*.applicationName() == ['CertScanner-GUI']
            urbanCodeApplications[0].components().collect { [it.componentName(), it.baseDir(), it.fileIncludePatterns()] } ==
                    [['CertScanner-GUI-app', 'gui/build/libs', '*.jar']]
            sshTargets == [(RD): SshTargetDto.builder().deployDir('/opt/ta/CertScanner/gui/deployment').build(),
                           (QC): SshTargetDto.builder().deployDir('/opt/ta/CertScanner/gui/deployment').build()]
        }
        with(api) {
            [build.tool(), build.buildPath(), build.command().tasks(), build.command().flags()] ==
                    [MAVEN, 'target/*.jar', ['clean', 'verify'], ['-B', '-U']]
            build.command().environment() == ['MAVEN_OPTS=-Xms512m -Xmx1g']
            [deployment.target(), deployment.appName(), deployment.artifactName()] ==
                    [OPENSHIFT, 'certscanner-api', 'certscanner-api.jar']
            with(openShiftTargets[RD]) {
                [projectBuild(), dockerRepoPush(), certDir(), nexusAuthFile(), projectDeployment(), configPath(),
                 routeHostname()] == ['ta-certscanner-build', 'docker-qc.tools.bbh.com/ta/certscanner-api',
                                      '/etc/pki/openshift', '/home/jenkins/.docker/nexus-auth.json', 'ta-certscanner-rd',
                                      'openshift/config-rd.yaml', 'certscanner-api-rd.apps.ocp-rd.testbbh.com']
            }
            openShiftTargets[QC].projectDeployment() == 'ta-certscanner-qc'
            sonar.command().tasks() == ['sonar:sonar']
        }
    }

    def "a rendered #type pipeline config names the pipeline's type and agents and leaves its jobs to the pipeline"() {
        given:
        def certScanner = product(services: [[name: 'gui', id: 10L, settings: MAVEN_VM]])
        Map config = builder.pipelineConfig(certScanner, certScanner.services()[0], pipeline(type: type,
                agentLabels: ['linux-agent', 'docker'], extendedPipelineJob: 'CERT/gui-extended',
                securityPipelineJob: 'CERT/gui-security'))

        when:
        def result = imported(new Yaml().dump(config))

        then:
        result.pipeline() == new ImportedPipeline(type, ['linux-agent', 'docker'])
        result.notes() == notes
        result.services()[0].notes() == serviceNotes
        asRun(rendered(result.services()[0])) == asRun(builder.productConfig(certScanner))

        where:
        type     || notes                                             | serviceNotes
        SECURITY || []                                                | [problem('jenkins.pipeline.extendedPipeline', PIPELINE)]
        EXTENDED || [problem('pipeline.securityPipeline', PIPELINE)] | []
        NEXUS_IQ || []                                                | []
    }

    def "a file of one project is named by the request or by its pipeline section, and its defaults fill in what it lacks"() {
        when:
        def named = imported('appId: 109f44ac-cc06-4ca0-884e-d944904f7019\nbuildTool: maven', 'gui')
        def fromPipeline = imported('''
            pipeline:
              type: full
              projectNames: api
            platform:
              jenkinsUrl: https://jenkins.bbh.com
              jenkinsLibrary: DevSecOpsJenkinsLibrary
            defaults:
              buildTool: maven
              sourceDir: app
              tests:
                maxParallel: 20
              coverage:
                minLine: 80
            appId: 109f44ac-cc06-4ca0-884e-d944904f7019
            deployTarget: openshift
            '''.stripIndent())

        then:
        named.services()*.name() == ['gui']
        named.services()[0].service().build().tool() == MAVEN
        fromPipeline.services()*.name() == ['api']
        fromPipeline.pipeline() == new ImportedPipeline(FULL, [])
        with(fromPipeline.services()[0].service()) {
            [build.tool(), build.sourceDir(), deployment.target()] == [MAVEN, 'app', OPENSHIFT]
        }
        fromPipeline.notes() == [problem('platform.jenkinsUrl', GLOBAL), problem('defaults.coverage.minLine', GLOBAL)]
        fromPipeline.services()[0].notes() == []
        imported('appId: 109f44ac-cc06-4ca0-884e-d944904f7019').services()[0].service().deployment().target() == VM
    }

    def "a value the portal cannot hold is left out with a note while the rest of the service is imported"() {
        when:
        def result = imported('''
            projects:
              gui:
                buildTool: ant
                appId: 109f44ac-cc06-4ca0-884e-d944904f7019
                build:
                  gradle:
                    tasks: clean build
                    env: [A=1]
                  maven:
                    goals: [verify]
                tests:
                  maxParallel: many
                  smoke:
                    required: perhaps
                    defaults:
                      token: s3cret-default
                    jobs:
                      - job: smoke
                        auth: {$class: TokenAuth, token: s3cret}
                        token: s3cret
                      - [nested]
                    urls: https://jenkins.bbh.com/job/smoke
                scm:
                  bitbucket:
                    url: https://bitbucket.bbh.com/scm/ta/cert.git
                    authType: token
                    reviewers: alice, bob
                tools:
                  sonar:
                    projectKey: [a, b]
                  nexusIq:
                    application:
                      cert-gui: {scanPatterns: ['**/*.jar'], stage: release, serverUrl: https://iq.other.com}
                      cert-api: {scanPatterns: ['**/*.war']}
                deploy:
                  vm:
                    rd:
                      nexusDeliveryEnabled: false
                custom: value
            other: value
            '''.stripIndent())
        def gui = result.services()[0].service()

        then:
        result.notes() == [problem('other', UNKNOWN)]
        result.services()[0].notes() as Set == [
                problem('buildTool', 'not imported: expected one of gradle, maven, flutter'),
                problem('tests.maxParallel', 'not imported: expected a whole number'),
                problem('tests.smoke.required', 'not imported: expected true or false'),
                problem('tools.sonar.projectKey', 'not imported: expected a single value'),
                problem('build.gradle.env', 'not imported: expected a map of variables'),
                problem('build.maven', 'not imported: the service builds with gradle'),
                problem('tests.smoke.jobs[0].auth', 'not imported: expected CredentialsAuth with its credentials ID'),
                problem('tests.smoke.jobs[0].token', SECRET),
                problem('tests.smoke.jobs[1]', 'not imported: expected a map'),
                problem('tests.smoke.urls', 'not imported: expected a list'),
                problem('tests.smoke.defaults.token', SECRET),
                problem('tools.nexusIq.application.cert-gui.serverUrl', UNKNOWN),
                problem('tools.nexusIq.application.cert-api', 'not imported: ' + ONE_APPLICATION),
                problem('custom', UNKNOWN)] as Set
        !json.writeValueAsString(result).contains('s3cret')
        gui.build().tool() == GRADLE
        gui.build().command().tasks() == ['clean', 'build']
        gui.testJobs()*.job() == ['smoke']
        [gui.scm().authType(), gui.scm().reviewers()] == [BEARER, ['alice', 'bob']]
        gui.nexusIqApplications() == [new NexusIqApplicationDto('cert-gui', ['**/*.jar'], 'release', false)]
        gui.deployment().nexusDeliveryDisabled()
    }

    def "versions and dates stay text, YAML booleans and whole numbers keep their type"() {
        expect:
        ConfigFileParser.parse('version: 1.10\ndate: 2024-01-01\nskip: yes\ncount: 12\nratio: 0.5', json) ==
                [version: '1.10', date: '2024-01-01', skip: true, count: 12, ratio: '0.5']
    }

    def "content that is not a config file is refused at its line and column, never echoing the file: #content"() {
        when:
        imported(content)

        then:
        def e = thrown(InvalidRequestException)
        e.problems()*.field == ['content']
        e.problems()*.message == [message]
        !e.problems()*.message.join().contains('s3cret')

        where:
        content                                                                     || message
        'asoc:\n  keySecret: s3cret\n  keyId: [bbh_key\n'                           || 'line 4, column 1: is not valid YAML'
        'asoc:\n  keyId: s3cret\n  keyId: bbh_key\n'                                || 'line 3, column 3: holds a duplicate key'
        'projects:\n\tgui: {}\n'                                                    || 'line 2, column 1: is not valid YAML'
        'asoc:\n  keySecret: "ab\\uZZZZs3cret"\n'                                   || 'line 2, column 19: is not valid YAML'
        'asoc:\n  keySecret: "ab\\qs3cret"\n'                                       || 'line 2, column 18: is not valid YAML'
        '{"projects": {"gui": '                                                     || 'line 1, column 22: is not valid JSON'
        '- gui\n- api\n'                                                            || 'line 1, column 1: ' + NO_PROJECT
        'projects: []\n'                                                            || 'line 1, column 1: ' + NO_PROJECT
        'projects:\n  gui: s3cret\n'                                                || 'line 1, column 1: ' + NO_PROJECT
        'version: "3.9"\nservices:\n  web:\n    image: nginx\n'                     || 'line 1, column 1: ' + NO_PROJECT
        'spring:\n  application:\n    name: ledger\nserver:\n  port: 8080\n'        || 'line 1, column 1: ' + NO_PROJECT
        'projects:\n' + (0..MAX_PROJECTS).collect { "  p$it: {appId: a}\n" }.join() || 'holds more than 50 projects'
        'projects:\n  svc: &x\n    buildTool: gradle\n    custom: *x\n'             || SELF_REFERENCE
        'projects:\n  svc:\n    custom: &y {inner: {back: *y}}\n'                   || SELF_REFERENCE
        'a: &l [x, *l]\n'                                                           || SELF_REFERENCE
        'a: &a [x]\nb: [' + (['*a'] * 21).join(', ') + ']\n'                        || LIMITS
        'a: ' + '[' * 31 + ']' * 31                                                 || LIMITS
        'a: ' + 'x' * MAX_LENGTH                                                    || LIMITS
    }

    private ConfigImportResponse imported(String content, String serviceName = null) {
        reader.read(ConfigFileParser.parse(content, json), serviceName)
    }

    private Map asRun(Map config) {
        projectsAsRun(config, global.defaultsConfig())
    }

    private static ServiceSettings gradleOpenShift(boolean nexusDeliveryDisabled) {
        settings(build: build(tool: GRADLE, autoSetup: true, buildPath: 'build/libs/*.jar', command: fullCommand('build')),
                unitTests: MAVEN_VM.unitTests(), tests: MAVEN_VM.tests(), testJobs: MAVEN_VM.testJobs(),
                deployment: deployment(target: OPENSHIFT, appName: 'cert', artifactName: 'cert.jar',
                        nexusDeliveryDisabled: nexusDeliveryDisabled),
                openShiftTargets: MAVEN_VM.openShiftTargets(), appScan: MAVEN_VM.appScan(), sonar: MAVEN_VM.sonar(),
                nexusIq: MAVEN_VM.nexusIq(), nexusIqApplications: MAVEN_VM.nexusIqApplications(), scm: MAVEN_VM.scm(),
                goldenFix: MAVEN_VM.goldenFix(), metrics: MAVEN_VM.metrics(), nodeBuild: MAVEN_VM.nodeBuild(),
                versionCatalogs: MAVEN_VM.versionCatalogs())
    }

    private Map rendered(ImportedService imported) {
        builder.productConfig(product(services: [[name: imported.name(), settings: imported.service().toDraft().settings()]]))
    }

    private static InvalidRequestException.FieldProblem problem(String field, String message) {
        new InvalidRequestException.FieldProblem(field, message)
    }
}
