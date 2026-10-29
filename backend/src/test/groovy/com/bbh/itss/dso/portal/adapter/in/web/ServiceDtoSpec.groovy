package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.adapter.RecordMapper
import com.bbh.itss.dso.portal.domain.catalog.FlutterSettings
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings
import com.bbh.itss.dso.portal.domain.catalog.NexusIqApplication
import com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings
import com.bbh.itss.dso.portal.domain.catalog.NodeBuildSettings
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

import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.ABSOLUTE_FOLDER_MESSAGE
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.COMMAND_MESSAGE
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.IMAGE_TAG_MESSAGE
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.POWERSHELL_PATH_MESSAGE
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.RELATIVE_FOLDER_MESSAGE
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.REPOSITORY_PATH_MESSAGE
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
        'a SAST scan name with a /'   | [appScan: [applicationId: APP_ID, scanName: 'cert/gui']]      || 'appScan.scanName'                               | "may contain letters, digits, '.', '_' and '-'"
        'a long AppScan key ID'       | [appScan: [applicationId: APP_ID, keyId: 'k' * 201]]          || 'appScan.keyId'                                  | 'size must be between 0 and 200'
        'two Nexus IQ applications'   | [nexusIqApplications: [[application: 'a', scanPatterns: ['*.jar']], [application: 'b', scanPatterns: ['*.war']]]] || 'nexusIqApplications' | 'a service scans one Nexus IQ application'
        'a Node.js folder above'      | [nodeBuild: [directory: 'web/../..']]                         || 'nodeBuild.directory'                            | RELATIVE_FOLDER_MESSAGE
        'an absolute Node.js folder'  | [nodeBuild: [directory: '/opt/web']]                          || 'nodeBuild.directory'                            | RELATIVE_FOLDER_MESSAGE
        'a Node.js folder with quotes'| [nodeBuild: [directory: '"web"']]                             || 'nodeBuild.directory'                            | RELATIVE_FOLDER_MESSAGE
        'a relative Node.js home'     | [nodeBuild: [nodePath: 'tools/node-20']]                      || 'nodeBuild.nodePath'                             | ABSOLUTE_FOLDER_MESSAGE
        'a Node.js home with a quote' | [nodeBuild: [nodePath: "/opt/node'20"]]                       || 'nodeBuild.nodePath'                             | ABSOLUTE_FOLDER_MESSAGE
        'two commands on one line'    | [nodeBuild: [commands: ['npm ci\nnpm test']]]                 || 'nodeBuild.commands[0].<list element>'           | COMMAND_MESSAGE
        'a long command'              | [nodeBuild: [commands: ['n' * 501]]]                          || 'nodeBuild.commands[0].<list element>'           | 'size must be between 0 and 500'
        'more than 20 commands'       | [nodeBuild: [commands: names(21)]]                            || 'nodeBuild.commands'                             | 'size must be between 0 and 20'
        'a Node.js variable alone'    | [nodeBuild: [environment: ['CI']]]                            || 'nodeBuild.environment[0].<list element>'        | 'write each variable as NAME=value'
        'more than 30 variables'      | [nodeBuild: [environment: names(31).collect { "$it=1" as String }]] || 'nodeBuild.environment'                | 'size must be between 0 and 30'
        'an npm registry without http'| [nodeBuild: [registry: 'nexus.bbh.com/npm-group']]            || 'nodeBuild.registry'                             | URL_MESSAGE
        'a long Node.js installation' | [nodeBuild: [installationName: 'n' * 201]]                    || 'nodeBuild.installationName'                     | 'size must be between 0 and 200'
        'a long .npmrc credentials ID'| [nodeBuild: [npmrcCredentialsId: 'n' * 201]]                  || 'nodeBuild.npmrcCredentialsId'                   | 'size must be between 0 and 200'
        'a Node.js timeout over 4 h'  | [nodeBuild: [timeoutMinutes: 241]]                            || 'nodeBuild.timeoutMinutes'                       | 'must be less than or equal to 240'
        'a Node.js timeout of zero'   | [nodeBuild: [timeoutMinutes: 0]]                              || 'nodeBuild.timeoutMinutes'                       | 'must be greater than or equal to 1'
        'an absolute version catalog' | [versionCatalogs: ['/gradle/libs.versions.toml']]             || 'versionCatalogs[0].<list element>'              | REPOSITORY_PATH_MESSAGE
        'a catalog above the root'    | [versionCatalogs: ['gradle/../../libs.versions.toml']]        || 'versionCatalogs[0].<list element>'              | REPOSITORY_PATH_MESSAGE
        'a catalog with a backslash'  | [versionCatalogs: ['gradle\\libs.versions.toml']]             || 'versionCatalogs[0].<list element>'              | REPOSITORY_PATH_MESSAGE
        'a catalog with a space'      | [versionCatalogs: ['my catalog.toml']]                        || 'versionCatalogs[0].<list element>'              | REPOSITORY_PATH_MESSAGE
        'an empty version catalog'    | [versionCatalogs: ['']]                                       || 'versionCatalogs[0].<list element>'              | 'size must be between 1 and 300'
        'more than 20 catalogs'       | [versionCatalogs: names(21)]                                  || 'versionCatalogs'                                | 'size must be between 0 and 20'
    }

    def "a Node.js build may run any command line from a Windows or Unix home and catalogs may be globs"() {
        expect:
        validator.validate(request(toJson(serviceJson(nodeBuild: [enabled: true, directory: 'web/app',
                commands: ['npm ci --prefer-offline', 'npm run build -- --configuration=production && echo "done"'],
                nodePath: nodePath, environment: ['NODE_OPTIONS=--max-old-space-size=4096'],
                registry: 'https://nexus.bbh.com/repository/npm-group/', timeoutMinutes: 240],
                versionCatalogs: ['gradle/libs.versions.toml', '**/*.versions.toml', 'platform/pom.xml'])))).isEmpty()

        where:
        nodePath << ['/opt/node-20', 'C:\\Program Files\\nodejs', 'D:/tools/node', '\\\\agent-share\\node 20']
    }

    def "a service sent without its Node.js build or shared catalogs gets none"() {
        when:
        def settings = request(toJson(serviceJson())).toDraft().settings()

        then:
        settings.nodeBuild() == NodeBuildSettings.NONE
        settings.versionCatalogs() == []
        request(toJson(serviceJson(nodeBuild: [directory: 'web']))).toDraft().settings().nodeBuild() ==
                NodeBuildSettings.builder().directory('web').build()
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
                .nodeBuild(new NodeBuildSettings(true, 'frontend', ['npm ci', 'npm run build'], 'NodeJS-20', null,
                        ['CI=true'], 'https://nexus.bbh.com/repository/npm-group/', 'npmrc-file', 30, false))
                .unitTests(UnitTestSettings.builder().command(command(['test'])).resultPattern('**/TEST-*.xml')
                        .allowEmptyResults(true).build())
                .tests(TestSettings.builder().maxParallel(5).smokeMaxParallel(1).regressionMaxParallel(2)
                        .performanceMaxParallel(3).smokeRequired(true).regressionRequired(true)
                        .performanceRequired(true).build())
                .testJobs([TestJob.of(SMOKE, 'smoke', null, 'CERT/gui-smoke', 10)])
                .deployment(deployment(appName: 'gui', nexusDeliveryDisabled: true))
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
                        compileCommand: command(['compile']), scanName: 'cert-gui-sast'))
                .sonar(SonarSettings.builder().projectName('Cert').projectKey('cert-gui').command(command(['sonar:sonar']))
                        .badgeCredentialsId('sonar-badge').build())
                .nexusIq(new NexusIqSettings('https://iq.bbh.com', 'iq-creds'))
                .nexusIqApplications([NexusIqApplication.of('cert', ['**/*.war'])])
                .scm(ScmSettings.builder().repositoryUrl('https://bitbucket.bbh.com/scm/ta/cert.git')
                        .credentialsId('bb-creds').apiUrl('https://bitbucket.bbh.com/rest/api/1.0')
                        .workspace('ta-workspace').projectKey('TA').repoSlug('cert-gui').build())
                .goldenFix(GoldenFixPolicy.inherit(false))
                .versionCatalogs(['gradle/libs.versions.toml'])
                .metrics(new MetricsSettings(false, 'cert-gui', 'qc', null, null))
                .flutter(FlutterSettings.builder().platform(WEB).modules(['app']).testModules([]).testSubmodules([])
                        .testSubplugins([]).signingPasswordCredentialsId('s').prodLicenseCredentialsId('p')
                        .testLicenseCredentialsId('t').sonarFlutterPlugin(true).build())
                .build()
    }
}
