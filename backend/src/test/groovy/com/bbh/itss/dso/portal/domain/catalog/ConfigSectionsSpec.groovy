package com.bbh.itss.dso.portal.domain.catalog

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType.BASIC
import static com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType.BEARER
import static com.bbh.itss.dso.portal.domain.catalog.BitbucketType.CLOUD
import static com.bbh.itss.dso.portal.domain.catalog.BitbucketType.SERVER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM
import static com.bbh.itss.dso.portal.domain.catalog.MetricsSettings.DEFAULTS
import static com.bbh.itss.dso.portal.domain.catalog.ToolCommand.NONE
import static com.bbh.itss.dso.portal.domain.shared.Sections.messages
import static com.bbh.itss.dso.portal.domain.shared.Sections.problems
import static com.bbh.itss.dso.portal.domain.shared.Sections.reported
import static com.bbh.itss.dso.portal.domain.shared.Sections.written

class ConfigSectionsSpec extends Specification {

    static final String APP_ID = '109f44ac-cc06-4ca0-884e-d944904f7019'
    static final String REPO = 'https://bitbucket.bbh.com/scm/ta/cert.git'

    def "build settings default the source directory, trim the paths and start without a command"() {
        when:
        def build = new BuildSettings(MAVEN, ' ', ' /opt/jdk-17 ', null, ' target/cert.jar ', null)

        then:
        build == new BuildSettings(MAVEN, '.', '/opt/jdk-17', false, 'target/cert.jar', NONE)
        written(build) == [buildTool: 'maven', sourceDir: '.', javaPath: '/opt/jdk-17', build: [buildPath: 'target/cert.jar']]
        new BuildSettings(GRADLE, null, ' ', false, ' ', null).javaPath() == null
        new BuildSettings(GRADLE, null, null, false, ' ', null).buildPath() == null
    }

    def "a Maven build writes its goals, flags, directory, Maven installation and environment under build.maven"() {
        given:
        def command = ToolCommand.builder().tasks(['clean', 'verify']).flags(['-B', '-s', 'settings.xml'])
                .directory(' gui ').mavenHome(' /opt/maven ').environment(['MAVEN_OPTS=-Xmx1g']).build()

        expect:
        written(new BuildSettings(MAVEN, 'gui', '/jdk', false, null, command)) ==
                [buildTool: 'maven', sourceDir: 'gui', javaPath: '/jdk',
                 build    : [maven: [goals : ['clean', 'verify'], flags: ['-B', '-s', 'settings.xml'], dir: 'gui',
                                     mvnPath: '/opt/maven', env: [MAVEN_OPTS: '-Xmx1g']]]]
    }

    def "automatic build tool setup is written only when it is enabled, and a Flutter build writes no command"() {
        expect:
        written(new BuildSettings(GRADLE, 'app', null, true, null, ToolCommand.of(['build'], []))) ==
                [buildTool: 'gradle', sourceDir: 'app', buildToolAutoSetup: true, build: [gradle: [tasks: ['build']]]]
        written(new BuildSettings(FLUTTER, null, null, false, 'build/app.apk', ToolCommand.of(['assemble'], ['-q']))) ==
                [buildTool: 'flutter', sourceDir: '.', build: [buildPath: 'build/app.apk']]
    }

    def "a #tool build with java path #javaPath, auto setup #autoSetup and tasks #tasks reports #fields"() {
        expect:
        problems(new BuildSettings(tool, null, javaPath, autoSetup, null, ToolCommand.of(tasks, []))) == fields

        where:
        tool    | javaPath | autoSetup | tasks     || fields
        GRADLE  | null     | false     | ['build'] || ['javaPath']
        MAVEN   | null     | null      | ['verify']|| ['javaPath']
        GRADLE  | '/jdk'   | false     | []        || ['command.tasks']
        MAVEN   | null     | true      | []        || ['command.tasks']
        GRADLE  | null     | false     | []        || ['javaPath', 'command.tasks']
        GRADLE  | '/jdk'   | false     | ['build'] || []
        MAVEN   | null     | true      | ['verify']|| []
        FLUTTER | null     | false     | []        || ['javaPath']
        FLUTTER | '/jdk'   | true      | []        || ['autoSetup']
        FLUTTER | null     | true      | []        || ['javaPath', 'autoSetup']
        FLUTTER | '/jdk'   | false     | []        || []
    }

    def "OpenShift deployment needs the application and artifact names"() {
        expect:
        problems(new DeploymentSettings(OPENSHIFT, ' ', null, 'gui-1.0.jar')) == ['appName', 'artifactName']
        problems(new DeploymentSettings(OPENSHIFT, 'gui', null, null)) == ['artifactName']
        problems(new DeploymentSettings(OPENSHIFT, null, 'gui.jar', null)) == ['appName']
        problems(new DeploymentSettings(OPENSHIFT, 'gui', 'gui.jar', null)) == []
        problems(new DeploymentSettings(VM, null, null, null)) == []
        messages(new DeploymentSettings(OPENSHIFT, null, null, null)) ==
                ['is required for OpenShift deployment', 'is required for OpenShift deployment']
    }

    def "deployment settings write the target, the names and the base artifact name"() {
        when:
        def deployment = new DeploymentSettings(OPENSHIFT, ' gui ', ' gui.jar ', ' gui-1.0.0.jar ')

        then:
        written(deployment) == [deployTarget    : 'openshift', appName: 'gui', artifactName: 'gui.jar',
                                baseArtifactName: 'gui-1.0.0.jar']
        written(new DeploymentSettings(VM, ' ', '', ' ')) == [deployTarget: 'vm']
        new DeploymentSettings(VM, ' ', '', ' ') == new DeploymentSettings(VM, null, null, null)
    }

    def "AppScan settings store the application ID in lower case and start at the library's defaults"() {
        when:
        def appScan = new AppScanSettings(' 109F44AC-CC06-4CA0-884E-D944904F7019 ', ' ', [' src ', '', 'src', null],
                null, null, null, null, null, ' ', null, null, ' ', ' ', ' ', null)

        then:
        appScan == new AppScanSettings(APP_ID, null, ['src'], [], true, false, false, false, null, NONE, false, null,
                null, null, null)
        written { appScan.writeTo(it, GRADLE) } == [appId: APP_ID, includedDirs: 'src', dast: [enabled: false]]
        written { AppScanSettings.of(APP_ID).writeTo(it, MAVEN) } == [appId: APP_ID, dast: [enabled: false]]
        AppScanSettings.of(null).applicationId() == null
    }

    def "every AppScan option that differs from the library's default is written"() {
        given:
        def compileCommand = ToolCommand.builder().tasks(['compileJava']).flags(['--offline']).directory('gui')
                .mavenHome('/opt/maven').environment(['JAVA_OPTS=-Xmx2g']).build()
        def appScan = new AppScanSettings(APP_ID, ' cert-gui ', ['src/main', ' lib '], ['src/test'], false, true, true,
                true, ' /opt/appscan ', compileCommand, true, ' cert-gui-dast ', ' https://rdl1.testbbh.com ', ' p-1 ',
                null)

        expect:
        written { appScan.writeTo(it, GRADLE) } ==
                [appId      : APP_ID, includedDirs: 'src/main,lib', excludedDirs: 'src/test', appscanPath: '/opt/appscan',
                 asoc       : [doCompile: false, sourceCodeOnly: true, useAppScanConfig: true, insecureTls: true,
                               gradle   : [tasks: ['compileJava'], flags: ['--offline'], dir: 'gui',
                                           env  : [JAVA_OPTS: '-Xmx2g']]],
                 sast       : [scanName: 'cert-gui'],
                 dast       : [enabled: true, scanName: 'cert-gui-dast', targetUrl: 'https://rdl1.testbbh.com',
                               presenceId: 'p-1']]
    }

    def "the AppScan compile command goes under asoc.#key for #tool"() {
        given:
        def appScan = AppScanSettings.builder().applicationId(APP_ID)
                .compileCommand(ToolCommand.builder().tasks(['compile']).mavenHome('/opt/maven').build()).build()

        expect:
        written { appScan.writeTo(it, tool) }.asoc == asoc

        where:
        tool    || key      | asoc
        GRADLE  || 'gradle' | [gradle: [tasks: ['compile']]]
        MAVEN   || 'maven'  | [maven: [goals: ['compile'], mvnPath: '/opt/maven']]
        FLUTTER || 'none'   | null
    }

    def "enabled DAST needs a target URL"() {
        expect:
        reported { dast(null).validate(it, GRADLE) }.collect { [it.field, it.message] } ==
                [['dastTargetUrl', 'is required when DAST is enabled']]
        problems { dast('https://x').validate(it, GRADLE) } == []
        problems { AppScanSettings.of(APP_ID).validate(it, MAVEN) } == []
    }

    def "a #tool compile command that sets #description reports #fields"() {
        given:
        def appScan = AppScanSettings.builder().applicationId(APP_ID).compile(compile).compileCommand(command).build()

        expect:
        problems { appScan.validate(it, tool) } == fields

        where:
        description              | tool    | compile | command                                                         || fields
        'nothing'                | GRADLE  | true    | NONE                                                            || []
        'only a step label'      | GRADLE  | true    | ToolCommand.builder().label('Compile').build()                  || ['compileCommand.tasks']
        'only returnStdout'      | MAVEN   | true    | ToolCommand.builder().returnStdout(true).build()                || ['compileCommand.tasks']
        'a label, not compiling' | GRADLE  | false   | ToolCommand.builder().label('Compile').build()                  || []
        'a label'                | FLUTTER | true    | ToolCommand.builder().label('Compile').build()                  || []
        'tasks and a label'      | GRADLE  | true    | ToolCommand.builder().tasks(['classes']).label('Compile').build() || []
    }

    def "SonarQube settings trim their values and switch the badges off by default"() {
        when:
        def sonar = new SonarSettings(' ', ' cert ', ' ', ' ', ' ', ' ', null, null, null, null)

        then:
        sonar == new SonarSettings(null, 'cert', null, null, null, null, false, false, NONE, null)
        SonarSettings.of(' CertScanner ', 'cert', null) == new SonarSettings('CertScanner', 'cert', null, null, null, null,
                false, false, NONE, null)
        SonarSettings.NONE == new SonarSettings(' ', '', null, null, null, null, false, false, null, null)
    }

    def "SonarQube settings write every tools.sonar key with the analysis command of the build tool"() {
        given:
        def sonar = new SonarSettings(' CertScanner ', ' cert-scanner ', ' SonarQube BBH ', ' sonar-creds ',
                ' sonar-token ', ' sqb_1a2b ', true, true, ToolCommand.of(['sonar:sonar'], ['-Dsonar.branch.name=main']), null)

        expect:
        written { sonar.writeTo(it, MAVEN) } ==
                [tools: [sonar: [projectName     : 'CertScanner', projectKey: 'cert-scanner',
                                 installationName: 'SonarQube BBH', credentialsId: 'sonar-creds',
                                 authToken       : 'sonar-token', badgeToken: 'sqb_1a2b', addBadges: true, fullBadges: true,
                                 maven           : [goals: ['sonar:sonar'], flags: ['-Dsonar.branch.name=main']]]]]
        written { sonar.writeTo(it, GRADLE) }.tools.sonar.gradle == [tasks: ['sonar:sonar'], flags: ['-Dsonar.branch.name=main']]
        written { sonar.writeTo(it, FLUTTER) }.tools.sonar.keySet() as List ==
                ['projectName', 'projectKey', 'installationName', 'credentialsId', 'authToken', 'badgeToken', 'addBadges',
                 'fullBadges']
        written { SonarSettings.of('CertScanner', 'cert', null).writeTo(it, GRADLE) } ==
                [tools: [sonar: [projectName: 'CertScanner', projectKey: 'cert']]]
        written { SonarSettings.NONE.writeTo(it, GRADLE) } == [:]
    }

    def "a #tool SonarQube project with key #key and tasks #tasks reports #fields"() {
        expect:
        problems { SonarSettings.of(null, key, ToolCommand.of(tasks, [])).validate(it, tool) } == fields

        where:
        tool    | key    | tasks           || fields
        GRADLE  | 'cert' | []              || ['command.tasks']
        MAVEN   | 'cert' | []              || ['command.tasks']
        GRADLE  | null   | []              || []
        FLUTTER | 'cert' | []              || []
        MAVEN   | 'cert' | ['sonar:sonar'] || []
        GRADLE  | 'cert' | ['sonarqube']   || []
    }

    def "a SonarQube command that sets only a step label or the output switch needs its tasks without a project key too"() {
        expect:
        problems { SonarSettings.of(null, null, ToolCommand.builder().label('Analyse').build()).validate(it, GRADLE) } ==
                ['command.tasks']
        problems { SonarSettings.of(null, null, ToolCommand.builder().returnStdout(true).build()).validate(it, MAVEN) } ==
                ['command.tasks']
    }

    def "a missing #tool command of the #stage is explained in the words of the build tool"() {
        expect:
        messages(validation) == [message]

        where:
        tool     | stage      | validation                                                                  || message
        'Maven'  | 'build'    | new BuildSettings(MAVEN, null, '/jdk', false, null, null)                   || 'add the Maven goals of the build, for example clean verify'
        'Gradle' | 'build'    | new BuildSettings(GRADLE, null, '/jdk', false, null, null)                  || 'add the Gradle tasks of the build, for example clean build'
        'JDK'    | 'build'    | new BuildSettings(GRADLE, null, null, false, null, ToolCommand.of(['build'], [])) || 'set the JDK path or enable automatic build tool setup, the unit tests stage needs one of them'
        'Maven'  | 'analysis' | { SonarSettings.of(null, 'cert', null).validate(it, MAVEN) }                || 'add the Maven goals of the analysis, for example sonar:sonar'
        'Gradle' | 'analysis' | { SonarSettings.of(null, 'cert', null).validate(it, GRADLE) }               || 'add the Gradle tasks of the analysis, for example sonarqube'
    }

    def "a Nexus IQ application keeps each scan pattern once and defaults the stage to build"() {
        expect:
        new NexusIqApplication(' cert ', ['**/*.jar', ' ', '**/*.jar', ' **/*.war '], ' ', null) ==
                new NexusIqApplication('cert', ['**/*.jar', '**/*.war'], 'build', false)
        NexusIqApplication.of(' cert ', null) == new NexusIqApplication('cert', [], 'build', false)
        new NexusIqSettings(' ', ' ', ' ') == NexusIqSettings.NONE
    }

    def "one Nexus IQ application is written in the string form, with the server override and the SCA scan name"() {
        expect:
        written {
            new NexusIqSettings(' https://iq.bbh.com ', null, ' cert-sca ')
                    .writeTo(it, [new NexusIqApplication('cert', ['**/*.jar'], ' stage-release ', true)])
        } == [tools: [nexusIq: [serverUrl: 'https://iq.bbh.com', application: 'cert', scanPatterns: ['**/*.jar'],
                                stage    : 'stage-release', failOnNetworkError: true]],
              sca  : [scanName: 'cert-sca']]
        written { NexusIqSettings.NONE.writeTo(it, []) } == [:]
    }

    def "several Nexus IQ applications are written in the map form, each with the server and credentials in effect"() {
        given:
        def applications = [NexusIqApplication.of('cert-gui', ['**/gui/*.jar']),
                            new NexusIqApplication('cert-batch', ['**/batch/*.jar'], 'release', true)]

        when:
        def nexusIq = written {
            it.set('tools.nexusIq.serverUrl', 'https://tools.bbh.com/IQ').set('tools.nexusIq.credentialsId', 'nexusiqP')
            new NexusIqSettings(null, 'cert-iq', null).writeTo(it, applications)
        }.tools.nexusIq

        then:
        nexusIq == [serverUrl  : 'https://tools.bbh.com/IQ', credentialsId: 'cert-iq',
                    application: ['cert-gui'  : [scanPatterns: ['**/gui/*.jar'], serverUrl: 'https://tools.bbh.com/IQ',
                                                 credentialsId: 'cert-iq', stage: 'build', failOnNetworkError: false],
                                  'cert-batch': [scanPatterns: ['**/batch/*.jar'], serverUrl: 'https://tools.bbh.com/IQ',
                                                 credentialsId: 'cert-iq', stage: 'release', failOnNetworkError: true]]]
    }

    def "every Nexus IQ application needs a name and scan patterns, and is listed once"() {
        given:
        def applications = [NexusIqApplication.of('cert', ['**/*.jar']), NexusIqApplication.of(' ', []),
                            NexusIqApplication.of('cert', ['**/*.war'])]

        expect:
        reported { NexusIqSettings.NONE.validate(it, applications) }.collect { [it.field, it.message] } ==
                [['nexusIqApplications[1].application', 'must not be blank'],
                 ['nexusIqApplications[1].scanPatterns', 'add at least one scan pattern for the Nexus IQ application'],
                 ['nexusIqApplications[2].application', 'is listed twice: each Nexus IQ application is scanned once']]
        problems { NexusIqSettings.NONE.validate(it, []) } == []
    }

    def "SCM settings trim their values, sign in with a password by default and keep each reviewer once"() {
        when:
        def scm = new ScmSettings(' ', ' ', null, null, ' ', ' ', [' alice ', 'alice', '', 'bob'], ' ', ' ', ' ', ' ')

        then:
        scm == new ScmSettings(null, null, BASIC, null, null, null, ['alice', 'bob'], null, null, null, null)
        ScmSettings.NONE == new ScmSettings(null, null, null, null, null, null, null, null, null, null, null)
        ScmSettings.of(" $REPO ", ' bb-creds ') ==
                new ScmSettings(REPO, 'bb-creds', BASIC, null, null, null, [], null, null, null, null)
        new ScmSettings(REPO, 'bb', null, null, null, null, null, ' https://api.bitbucket.org/2.0 ', ' ta ', ' TA ',
                ' cert ') == new ScmSettings(REPO, 'bb', BASIC, null, null, null, [], 'https://api.bitbucket.org/2.0',
                'ta', 'TA', 'cert')
    }

    def "a repository writes every scm.bitbucket key that is set, and nothing without its URL"() {
        expect:
        written(ScmSettings.NONE) == [:]
        written(new ScmSettings(null, 'bb-creds', BEARER, CLOUD, 'main', 'ssh://git@x/r.git', ['alice'],
                'https://api.bitbucket.org/2.0', 'ta', 'TA', 'cert')) == [:]
        written(new ScmSettings(" $REPO ", 'bb-creds', BEARER, SERVER, ' develop ',
                ' ssh://git@bitbucket.bbh.com:7999/ta/cert.git ', ['alice', '{0b9e-uuid}'],
                ' https://bitbucket.bbh.com/rest/api/1.0 ', null, ' TA ', ' cert-scanner ')) ==
                [scm: [bitbucket: [url        : REPO, credentialsId: 'bb-creds', authType: 'bearer', type: 'server',
                                   targetBranch: 'develop', cloneUrl: 'ssh://git@bitbucket.bbh.com:7999/ta/cert.git',
                                   reviewers  : ['alice', '{0b9e-uuid}'],
                                   apiUrl     : 'https://bitbucket.bbh.com/rest/api/1.0', projectKey: 'TA',
                                   repoSlug   : 'cert-scanner']]]
        written(ScmSettings.of(REPO, 'bb-creds')) ==
                [scm: [bitbucket: [url: REPO, credentialsId: 'bb-creds', authType: 'basic']]]
        written(ScmSettings.builder().repositoryUrl(REPO).authType(BASIC).type(CLOUD).workspace('ta-workspace')
                .repoSlug('cert').build()).scm.bitbucket ==
                [url: REPO, authType: 'basic', type: 'cloud', workspace: 'ta-workspace', repoSlug: 'cert']
    }

    def "a repository needs the credentials GoldenFix pushes with"() {
        expect:
        reported { ScmSettings.of(REPO, ' ').validate(it) }.collect { [it.field, it.message] } ==
                [['credentialsId', 'is required to push GoldenFix branches and open pull requests']]
        problems(ScmSettings.of(REPO, 'bb-creds')) == []
        problems(ScmSettings.NONE) == []
    }

    def "the Bitbucket repository keys need the repository URL they refine"() {
        expect:
        problems(ScmSettings.builder().apiUrl(apiUrl).workspace(workspace).projectKey(projectKey).repoSlug(repoSlug)
                .build()) == ['repositoryUrl']
        messages(ScmSettings.builder().apiUrl(apiUrl).workspace(workspace).projectKey(projectKey).repoSlug(repoSlug)
                .build()) ==
                ['is required when the Bitbucket API URL, workspace, project key or repository slug is set']
        problems(ScmSettings.builder().repositoryUrl(REPO).credentialsId('bb').apiUrl(apiUrl).workspace(workspace)
                .projectKey(projectKey).repoSlug(repoSlug).build()) == []

        where:
        apiUrl                          | workspace | projectKey | repoSlug
        'https://api.bitbucket.org/2.0' | null      | null       | null
        null                            | 'ta'      | null       | null
        null                            | null      | 'TA'       | null
        null                            | null      | null       | 'cert'
    }

    def "metrics are on by default, use the test environment and default the project to the product code and service name"() {
        expect:
        new MetricsSettings(null, ' ', ' ', null, null) == DEFAULTS
        DEFAULTS == new MetricsSettings(true, null, 'test', null, null)
        new MetricsSettings(false, ' cert ', ' prod ', null, null) == new MetricsSettings(false, 'cert', 'prod', null, null)
        written(new MetricsSettings(null, 'CERT-gui', null, null, null)) == [influx: [enabled: true, project: 'CERT-gui', env: 'test']]
        written(new MetricsSettings(false, null, 'uat', null, null)) == [influx: [enabled: false, env: 'uat']]
        DEFAULTS.withDefaultProject('CERT', 'gui') == new MetricsSettings(true, 'CERT-gui', 'test', null, null)
        new MetricsSettings(false, null, 'uat', null, null).withDefaultProject('CERT', 'gui') == new MetricsSettings(false, 'CERT-gui', 'uat', null, null)
        new MetricsSettings(true, 'cert-scanner', 'uat', null, null).with { it.withDefaultProject('CERT', 'gui').is(it) }
    }

    def "the per-service InfluxDB, SonarQube and AppScan secret overrides are written at the library's paths"() {
        expect:
        written(new MetricsSettings(true, 'CERT-gui', null, ' https://influx.bbh.com/write ', ' cert-influx ')) ==
                [influx: [enabled: true, url: 'https://influx.bbh.com/write', credentialsId: 'cert-influx',
                          project: 'CERT-gui', env: 'test']]
        written { SonarSettings.builder().serverUrl(' https://sonar.bbh.com ').build().writeTo(it, GRADLE) } ==
                [tools: [sonar: [serverUrl: 'https://sonar.bbh.com']]]
        written {
            AppScanSettings.builder().applicationId(APP_ID).secretCredentialsId(' cert-secret ').build()
                    .writeTo(it, GRADLE)
            new AppScanAccount('bbh_key', 'product-secret').writeTo(it)
        }.asoc == [token: 'cert-secret', keyId: 'bbh_key']
    }

    def "the AppScan account writes the key ID and the credential holding the secret"() {
        expect:
        written(new AppScanAccount(' bbh_key ', ' asoc-creds ')) == [asoc: [keyId: 'bbh_key', token: 'asoc-creds']]
        written(new AppScanAccount('bbh_key', ' ')) == [asoc: [keyId: 'bbh_key']]
        new AppScanAccount(' ', ' ') == new AppScanAccount(null, null)
    }

    private static AppScanSettings dast(String targetUrl) {
        AppScanSettings.builder().applicationId(APP_ID).dastEnabled(true).dastTargetUrl(targetUrl).build()
    }
}
