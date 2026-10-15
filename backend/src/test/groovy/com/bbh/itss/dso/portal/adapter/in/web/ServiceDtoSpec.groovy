package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.adapter.RecordMapper
import com.bbh.itss.dso.portal.domain.catalog.FlutterSettings
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings
import com.bbh.itss.dso.portal.domain.catalog.NexusIqApplication
import com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings
import com.bbh.itss.dso.portal.domain.catalog.OpenShiftTarget
import com.bbh.itss.dso.portal.domain.catalog.ScmSettings
import com.bbh.itss.dso.portal.domain.catalog.Service
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings
import com.bbh.itss.dso.portal.domain.catalog.SshTarget
import com.bbh.itss.dso.portal.domain.catalog.TestJob
import com.bbh.itss.dso.portal.domain.catalog.TestSettings
import com.bbh.itss.dso.portal.domain.catalog.UnitTestSettings
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeApplicationSettings
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeComponent
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeSettings
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.IMAGE_TAG_MESSAGE
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.POWERSHELL_PATH_MESSAGE
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.SHELL_SAFE_MESSAGE
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL_MESSAGE
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform.WEB
import static com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy.INHERITED
import static com.bbh.itss.dso.portal.domain.catalog.Region.QC
import static com.bbh.itss.dso.portal.domain.catalog.Region.RD
import static com.bbh.itss.dso.portal.domain.catalog.TestSettings.DEFAULTS
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.SMOKE
import static com.bbh.itss.dso.portal.domain.catalog.UnitTestSettings.NONE
import static com.bbh.itss.dso.portal.support.ApiJson.APP_ID
import static com.bbh.itss.dso.portal.support.ApiJson.build as buildJson
import static com.bbh.itss.dso.portal.support.ApiJson.service as serviceJson
import static com.bbh.itss.dso.portal.support.Json.toJson
import static com.bbh.itss.dso.portal.support.Fixtures.appScan
import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.deployment
import static jakarta.validation.Validation.buildDefaultValidatorFactory

class ServiceDtoSpec extends Specification {

    @Shared
    Validator validator = buildDefaultValidatorFactory().validator

    @Shared
    JsonMapper json = JsonMapper.builder().build()

    def "a service with every section travels through the API and back unchanged"() {
        given:
        def settings = fullSettings()

        when:
        def dto = ServiceDto.from(new Service(7L, 'gui', ' Angular front end ', 0, settings))

        then:
        dto.description() == 'Angular front end'
        dto.sshTargets().keySet() as List == [RD, QC]
        dto.scm().repoSlug() == 'cert-gui'
        request(json.writeValueAsString(dto)) == dto
        validator.validate(dto).isEmpty()
        with(dto.toDraft()) {
            id() == 7L
            name() == 'gui'
            it.settings() == settings
        }
    }

    def "a request with only the required sections gets the defaults of the others"() {
        when:
        def settings = request(toJson(serviceJson(sshTargets: [RD: null, QC: [host: 'qc.host']]))).toDraft().settings()

        then:
        settings.unitTests() == NONE
        settings.tests() == DEFAULTS
        settings.goldenFix() == INHERITED
        settings.metrics() == MetricsSettings.DEFAULTS
        settings.sshTargets() == [(QC): SshTarget.builder().host('qc.host').build()]
        settings.openShiftTargets() == [:]
    }

    def "a request is normalised the way the domain normalises before it is validated"() {
        when:
        def dto = request(toJson(serviceJson(
                appScan: [applicationId: " ${APP_ID.toUpperCase()} ", includedDirs: [' src ', '', 'src']],
                scm: [reviewers: [' alice ', 'alice']],
                sshTargets: [QC: [host: ' qc.host ', user: ' ']],
                urbanCodeApplications: [[applicationName: ' Cert ', environments: [' RD ', 'RD']]],
                testJobs: [[stage: 'SMOKE', job: ' CERT/smoke ', parameters: ' A=1 ']])))

        then:
        validator.validate(dto).isEmpty()
        dto.appScan().applicationId() == APP_ID
        dto.appScan().includedDirs() == ['src']
        dto.appScan().compile()
        dto.appScan().compileCommand() ==
                ServiceDto.ToolCommandDto.builder().tasks([]).flags([]).environment([]).returnStdout(false).build()
        dto.scm().reviewers() == ['alice']
        dto.sshTargets()[QC] == ServiceDto.SshTargetDto.builder().host('qc.host').build()
        dto.urbanCodeApplications()[0].applicationName() == 'Cert'
        dto.urbanCodeApplications()[0].environments() == ['RD']
        dto.urbanCodeApplications()[0].components() == []
        dto.testJobs()[0].job() == 'CERT/smoke'
        dto.testJobs()[0].parameters() == 'A=1'
        dto.metrics() == null
    }

    def "bean validation rejects #description and says what it expects"() {
        expect:
        validator.validate(request(toJson(serviceJson(changes)))).collect { [it.propertyPath.toString(), it.message] } ==
                [[property, message]]

        where:
        description                   | changes                                                       || property                                         | message
        'a service name with a space' | [name: 'Gui app']                                             || 'name'                                           | "use letters, digits, '.', '-' or '_', starting with a letter or digit"
        'a missing build tool'        | [build: [javaPath: '/jdk']]                                   || 'build.tool'                                     | 'must not be null'
        'more than 30 build tasks'    | [build: buildJson(command: [tasks: names(31)])]               || 'build.command.tasks'                            | 'size must be between 0 and 30'
        'a variable without a value'  | [build: buildJson(command: [environment: ['JAVA']])]          || 'build.command.environment[0].<list element>'    | 'write each variable as NAME=value'
        'a test job without time'     | [testJobs: [[stage: 'SMOKE', job: 'j', timeoutMinutes: 0]]]   || 'testJobs[0].timeoutMinutes'                    | 'must be greater than or equal to 1'
        'an SSH host with a space'    | [sshTargets: [RD: [host: 'rd host']]]                         || 'sshTargets[RD].host'                            | SHELL_SAFE_MESSAGE
        'a build path with a command' | [build: buildJson(buildPath: 'target/*.jar;rm')]              || 'build.buildPath'                                | SHELL_SAFE_MESSAGE
        'a Dockerfile with a space'   | [openShiftTargets: [RD: [dockerFilePath: 'my Dockerfile']]]   || 'openShiftTargets[RD].dockerFilePath'            | SHELL_SAFE_MESSAGE
        'a component without a name'  | [urbanCodeApplications: [[applicationName: 'C', components: [[:]]]]] || 'urbanCodeApplications[0].components[0].componentName' | 'must not be blank'
        'a DAST URL without http'     | [appScan: [applicationId: APP_ID, dastTargetUrl: 'ftp://x']]  || 'appScan.dastTargetUrl'                          | URL_MESSAGE
        'a workspace with a space'    | [scm: [workspace: 'ta workspace']]                            || 'scm.workspace'                                  | 'must not contain whitespace'
        'an author email without @'   | [goldenFix: [commitAuthorEmail: 'goldenfix.bbh.com']]         || 'goldenFix.commitAuthorEmail'                    | 'must be a well-formed email address'
        'a build tag with a space'    | [openShiftTargets: [QC: [buildTag: '1.0 rc']]]                || 'openShiftTargets[QC].buildTag'                  | IMAGE_TAG_MESSAGE
        'an image URL with a ~'       | [openShiftTargets: [QC: [internalDockerUrl: 'registry/~cert']]] || 'openShiftTargets[QC].internalDockerUrl'       | IMAGE_TAG_MESSAGE
        'an InfluxDB URL without http'| [metrics: [influxUrl: 'influx:8086']]                         || 'metrics.influxUrl'                              | URL_MESSAGE
        'a URL with a command'        | [scm: [repositoryUrl: 'https://bitbucket/$(id)']]             || 'scm.repositoryUrl'                              | URL_MESSAGE
        'a URL with a backtick'       | [metrics: [influxUrl: 'http://influx/`id`']]                  || 'metrics.influxUrl'                              | URL_MESSAGE
        'a URL with a double quote'   | [testJobs: [[stage: 'SMOKE', job: 'j', remoteJenkinsUrl: 'https://j/"x']]] || 'testJobs[0].remoteJenkinsUrl'     | URL_MESSAGE
        'a URL with a backslash'      | [appScan: [applicationId: APP_ID, dastTargetUrl: 'https://cert\\x']] || 'appScan.dastTargetUrl'                | URL_MESSAGE
        'a Nexus IQ app without name' | [nexusIqApplications: [[scanPatterns: ['**/*.jar']]]]         || 'nexusIqApplications[0].application'            | 'must not be blank'
        'a poll interval of zero'     | [tests: [smokePollIntervalSec: 0]]                            || 'tests.smokePollIntervalSec'                     | 'must be greater than or equal to 1'
        'a Maven home with a command' | [build: buildJson(command: [mavenHome: '/opt/maven;id'])]     || 'build.command.mavenHome'                        | SHELL_SAFE_MESSAGE
        'a Flutter plugin with $()'   | [flutter: [deliveryPlugin: 'deploy:deploy-file$(id)']]        || 'flutter.deliveryPlugin'                         | SHELL_SAFE_MESSAGE
        'an AppScan client with a ;'  | [appScan: [applicationId: APP_ID, clientPath: 'bin/appscan.bat;id']] || 'appScan.clientPath'                    | POWERSHELL_PATH_MESSAGE
        'a folder with a quote'       | [appScan: [applicationId: APP_ID, includedDirs: ["it's"]]]    || 'appScan.includedDirs[0].<list element>'        | 'one folder per entry, without commas or quotes'
    }

    def "an AppScan client path may use Windows separators"() {
        expect:
        validator.validate(request(toJson(serviceJson(appScan: [applicationId: APP_ID,
                clientPath: '\\SAClientUtil\\bin\\appscan.bat'])))).isEmpty()
    }

    private ServiceDto request(String body) {
        RecordMapper.map(json.readValue(body, ServiceDto), ServiceDto)
    }

    private static List<String> names(int count) {
        (1..count).collect { "t$it" as String }
    }

    static ServiceSettings fullSettings() {
        ServiceSettings.builder()
                .build(build(tool: MAVEN, buildPath: 'target/gui.war'))
                .unitTests(UnitTestSettings.builder().command(command(['test'])).resultPattern('**/TEST-*.xml')
                        .allowEmptyResults(true).build())
                .tests(TestSettings.builder().maxParallel(5).smokeMaxParallel(1).regressionMaxParallel(2)
                        .performanceMaxParallel(3).smokeRequired(true).regressionRequired(true)
                        .performanceRequired(true).build())
                .testJobs([TestJob.of(SMOKE, 'smoke', null, 'CERT/gui-smoke', 10)])
                .deployment(deployment(appName: 'gui'))
                .delivery(command(['deploy:deploy-file']))
                .urbanCode(new UrbanCodeSettings('BBH-RD', 'Deploy', true, false, true, false, true, 'desc', 'a=b'))
                .urbanCodeApplications([UrbanCodeApplicationSettings.of('Cert', 1, ['RD'], 'snap',
                        [UrbanCodeComponent.builder().componentName('cert-gui').baseDir('build/libs')
                                .fileIncludePatterns('*.war').incrementalVersion(false).build()])])
                .sshTargets([(QC): SshTarget.builder().host('qc.host').build(),
                             (RD): SshTarget.builder().host('rd.host').build()])
                .openShiftTargets([(QC): OpenShiftTarget.builder().dockerRepoPull('pull/cert')
                        .projectDeployment('cert-qc').skipConfigDeploy(true).build()])
                .appScan(appScan(dastEnabled: true, dastTargetUrl: 'https://rdl1.testbbh.com',
                        compileCommand: command(['compile'])))
                .sonar(SonarSettings.of('Cert', 'cert-gui', command(['sonar:sonar'])))
                .nexusIq(new NexusIqSettings('https://iq.bbh.com', 'iq-creds', null))
                .nexusIqApplications([NexusIqApplication.of('cert', ['**/*.war']),
                                      NexusIqApplication.of('cert-batch', ['**/batch/*.jar'])])
                .scm(ScmSettings.builder().repositoryUrl('https://bitbucket.bbh.com/scm/ta/cert.git')
                        .credentialsId('bb-creds').apiUrl('https://bitbucket.bbh.com/rest/api/1.0')
                        .workspace('ta-workspace').projectKey('TA').repoSlug('cert-gui').build())
                .goldenFix(GoldenFixPolicy.inherit(false))
                .metrics(new MetricsSettings(false, 'cert-gui', 'qc', null, null))
                .flutter(FlutterSettings.builder().platform(WEB).modules(['app']).testModules([]).testSubmodules([])
                        .testSubplugins([]).signingPasswordCredentialsId('s').prodLicenseCredentialsId('p')
                        .testLicenseCredentialsId('t').sonarFlutterPlugin(true).build())
                .build()
    }
}
