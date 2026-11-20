package com.bbh.itss.dso.portal.domain.catalog

import com.bbh.itss.dso.portal.domain.shared.ConfigTree
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.LIQUIBASE
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM
import static com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform.APK
import static com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy.INHERITED
import static com.bbh.itss.dso.portal.domain.catalog.Region.QC
import static com.bbh.itss.dso.portal.domain.catalog.Region.RD
import static com.bbh.itss.dso.portal.domain.catalog.TestJobType.LOCAL
import static com.bbh.itss.dso.portal.domain.catalog.TestJobType.REMOTE
import static com.bbh.itss.dso.portal.domain.catalog.TestSettings.DEFAULTS
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.REGRESSION
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.SMOKE
import static com.bbh.itss.dso.portal.domain.catalog.ToolCommand.NONE
import static com.bbh.itss.dso.portal.support.Fixtures.APP_ID
import static com.bbh.itss.dso.portal.support.Fixtures.JDK
import static com.bbh.itss.dso.portal.support.Fixtures.appScan
import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.database
import static com.bbh.itss.dso.portal.support.Fixtures.databaseSettings
import static com.bbh.itss.dso.portal.support.Fixtures.deployment
import static com.bbh.itss.dso.portal.support.Fixtures.fullSettings
import static com.bbh.itss.dso.portal.support.Fixtures.settings

class ServiceSettingsSpec extends Specification {

    static final String REPO = 'https://bitbucket.bbh.com/scm/ta/cert.git'
    static final SshTarget RD_HOST = SshTarget.builder().host('rd.host').build()
    static final OpenShiftTarget RD_PROJECT = OpenShiftTarget.builder().projectBuild('cert-build')
            .projectDeployment('cert-rd').build()
    static final UrbanCodeApplicationSettings UCD_APP = UrbanCodeApplicationSettings.of('Cert', 1, ['RD'], null,
            [UrbanCodeComponent.builder().componentName('cert-gui').baseDir('build').fileIncludePatterns('*.war')
                    .incrementalVersion(false).build()])

    def "sections left out take their defaults"() {
        expect:
        ServiceSettings.builder().build(build()).deployment(deployment()).appScan(appScan()).build() ==
                new ServiceSettings(build(), NodeBuildSettings.NONE, UnitTestSettings.NONE, DEFAULTS, [], deployment(),
                        NONE, NexusDeliverySettings.NONE, UrbanCodeSettings.DEFAULTS, [], [:], [:], appScan(),
                        SonarSettings.NONE, NexusIqSettings.NONE,
                        null, ScmSettings.NONE, INHERITED, null, VersionCatalogRepo.NONE, MetricsSettings.DEFAULTS,
                        FlutterSettings.NONE, DatabaseSettings.NONE, [:])
    }

    def "deployment targets that set nothing, or have no region, are dropped and the rest kept in region order"() {
        given:
        def ssh = new LinkedHashMap<Region, SshTarget>()
        ssh[QC] = SshTarget.builder().host('qc.host').build()
        ssh[RD] = SshTarget.builder().host(' ').build()
        ssh[null] = RD_HOST

        when:
        def settings = settings(sshTargets: ssh, openShiftTargets: [(RD): RD_PROJECT])

        then:
        settings.sshTargets().keySet() as List == [QC]
        settings.openShiftTargets().keySet() as List == [RD]
    }

    def "a Gradle service on a VM writes its UrbanCode and SSH deployment but no delivery or OpenShift section"() {
        when:
        def tree = written(settings(delivery: command(['publish']), urbanCodeApplications: [UCD_APP],
                sshTargets: [(RD): RD_HOST], openShiftTargets: [(RD): RD_PROJECT]))

        then:
        tree.get('build.gradle.tasks') == ['clean', 'build']
        tree.get('delivery') == null
        tree.get('deploy.vm.rd') == [host: 'rd.host']
        tree.get('deploy.vm.dod.applications')*.applicationName == ['Cert']
        tree.get('deploy.openshift') == null
        tree.get('flutter') == null
        written(settings(build: build(tool: MAVEN), delivery: command(['deploy:deploy-file'], ['-Did=bbh'])))
                .get('delivery') == [maven: [goals: ['deploy:deploy-file'], flags: ['-Did=bbh']]]
    }

    def "a Node.js build is written right after the build and the shared version catalogs under goldenFix"() {
        when:
        def tree = written(settings(nodeBuild: NodeBuildSettings.builder().enabled(true).directory('frontend').build(),
                goldenFix: GoldenFixPolicy.inherit(false), versionCatalogs: [' gradle/libs.versions.toml ', '',
                                                                             'gradle/libs.versions.toml']))

        then:
        tree.get('build') == [gradle: [tasks: ['clean', 'build']], node: [enabled: true, dir: 'frontend']]
        tree.get('build').keySet() as List == ['gradle', 'node']
        tree.get('goldenFix') == [enabled: false, versionCatalogs: ['gradle/libs.versions.toml']]
        written(settings(versionCatalogs: ['gradle/libs.versions.toml'])).get('goldenFix') ==
                [versionCatalogs: ['gradle/libs.versions.toml']]
        written(settings()).get('build.node') == null
        written(settings()).get('goldenFix') == null
    }

    def "shared version catalogs are stored and written relative to the repository root"() {
        expect:
        settings(versionCatalogs: ['./bom/pom.xml', 'bom/pom.xml/', 'gradle//deps.toml', '.']).versionCatalogs() ==
                ['bom/pom.xml', 'gradle/deps.toml']
    }

    def "a Flutter service cannot run a Node.js build before its build"() {
        expect:
        reported(settings(build: build(tool: FLUTTER, javaPath: null),
                nodeBuild: NodeBuildSettings.builder().enabled(true).build()))*.field.contains('nodeBuild.enabled')
        !reported(settings(build: build(tool: FLUTTER, javaPath: null),
                nodeBuild: NodeBuildSettings.builder().directory('frontend').build()))*.field.contains('nodeBuild.enabled')
    }

    def "a Liquibase service with database changes cannot run a Node.js build"() {
        given:
        def node = NodeBuildSettings.builder().enabled(true).build()

        expect:
        reported(databaseSettings(nodeBuild: node)).collect { [it.field, it.message] } == [['nodeBuild.enabled',
                'must be off for a service with database changes: the library runs no Node.js build for it']]
        reported(settings(build: build(tool: LIQUIBASE, command: NONE), nodeBuild: node)) == []
    }

    def "only a Liquibase service with database changes checks the folder of its SonarQube command: #folder"() {
        given:
        def sonar = SonarSettings.of(null, 'bbh-orders-db', ToolCommand.builder().directory(folder).build())

        expect:
        reported(databaseSettings(sonar: sonar)).collect { [it.field, it.message] } == problems
        reported(databaseSettings(sonar: sonar, database: DatabaseSettings.NONE, databaseTargets: [:])) == []
        reported(settings(sonar: SonarSettings.of(null, null, ToolCommand.builder().tasks(['sonar']).directory(folder)
                .build()))) == []

        where:
        folder   || problems
        'db'     || []
        '../db'  || [['sonar.command.directory', 'must be "." or a relative path inside the repository, without ".."']]
        'db/sql' || [['sonar.command.directory', 'does not hold the package folder db and the folder of the test'
                + ' packages db/test: sonar-scanner runs in that folder, and SonarQube analyses only the files under'
                + ' it: set the directory to a folder that holds the package folder and the folder of the test'
                + ' packages, or empty it']]
    }

    def "a Liquibase service with database changes writes no test stage as not required"() {
        given:
        def tests = TestSettings.builder().smokeRequired(false).build()

        expect:
        written(databaseSettings(tests: tests)).toMap().tests == null
        written(settings(build: build(tool: LIQUIBASE, command: NONE), tests: tests)).toMap().tests ==
                [smoke: [required: false]]
    }

    def "a service on OpenShift writes its OpenShift projects and nothing of the VM deployment"() {
        when:
        def tree = written(settings(deployment: deployment(target: OPENSHIFT, appName: 'cert',
                artifactName: 'cert.jar'), urbanCodeApplications: [UCD_APP], sshTargets: [(RD): RD_HOST],
                openShiftTargets: [(RD): RD_PROJECT]))

        then:
        tree.get('deploy.openshift.rd') == [projectBuildR: 'cert-build', projectDeploymentR: 'cert-rd']
        tree.get('deploy.vm') == null
    }

    def "only a Flutter service writes its Flutter section"() {
        given:
        def flutter = FlutterSettings.builder().platform(APK).modules(['app']).signingPasswordCredentialsId('sign')
                .prodLicenseCredentialsId('prod-license').testLicenseCredentialsId('test-license').build()

        when:
        def flutterTree = written(settings(build: build(tool: FLUTTER, javaPath: null), flutter: flutter))

        then:
        flutterTree.get('flutter.platform') == 'apk'
        flutterTree.get('build.credentialsId') == ['sign', 'prod-license', 'test-license']
        flutterTree.get('build.gradle') == null
        written(settings(flutter: flutter)).get('flutter') == null
    }

    def "each section reports its problems under its own name"() {
        given:
        def problems = new ValidationProblems()

        when:
        settings(build: build(javaPath: null), deployment: deployment(target: OPENSHIFT),
                appScan: appScan(dastEnabled: true), scm: ScmSettings.of(REPO, null),
                sonar: SonarSettings.of('Cert', 'cert', null),
                unitTests: UnitTestSettings.builder().resultPattern('**/TEST-*.xml').build())
                .validate(problems.at('services[3]'))

        then:
        problems.list()*.field == ['build.javaPath', 'unitTests.command.tasks', 'deployment.appName',
                                   'deployment.artifactName', 'openShiftTargets[RD].projectBuild',
                                   'openShiftTargets[RD].buildConfigPath', 'openShiftTargets[RD].dockerFilePath',
                                   'openShiftTargets[RD].buildContext', 'openShiftTargets[RD].dockerRepoPush',
                                   'openShiftTargets[RD].nexusAuthFile', 'appScan.dastTargetUrl',
                                   'sonar.command.tasks', 'scm.credentialsId'].collect { "services[3].$it" as String }
    }

    def "a Maven service on a VM needs its artifact path and delivery goals and a Flutter service what its stages read"() {
        expect:
        reported(settings(build: build(tool: MAVEN), delivery: NONE))*.field ==
                ['build.buildPath', 'delivery.tasks']
        reported(settings(build: build(tool: MAVEN), delivery: NONE))[0].message ==
                'is required for Maven on VMs without the artifacts of the Nexus delivery: the delivery then publishes the artifact found there'
        reported(settings(build: build(tool: FLUTTER, javaPath: null)))*.field ==
                ['build.javaPath', 'flutter.modules', 'flutter.testModules', 'flutter.signingPasswordCredentialsId',
                 'flutter.prodLicenseCredentialsId', 'flutter.testLicenseCredentialsId', 'flutter.deliveryGroup',
                 'flutter.deliveryArtifact', 'flutter.deliveryPlugin']
        reported(settings(build: build(tool: MAVEN), delivery: NONE,
                deployment: deployment(target: OPENSHIFT, appName: 'cert', artifactName: 'cert.jar'),
                openShiftTargets: [(RD): fullSettings().openShiftTargets()[RD]])) == []
    }

    def "a Maven service on a VM uploads its listed artifacts with delivery.push.maven and downloads them with delivery.pull"() {
        given:
        def upload = command(['deploy:deploy-file'], ['-DrepositoryId=bbh-snapshots', '-DgroupId=com.bbh.cert'])
        def delivery = NexusDeliverySettings.builder().artifacts(['target/*.jar', 'target/cert-docs.zip docs'])
                .group('com.bbh.cert').artifact('cert-gui')
                .pull(command(['org.apache.maven.plugins:maven-dependency-plugin:3.6.1:get'],
                        ['-DremoteRepositories=bbh::default::https://tools.bbh.com/nexus/repository/snapshots']))
                .pullArtifacts(['cert-gui.jar', 'cert-docs.zip docs']).pullOutputDirectory('nexus').build()
        def service = settings(build: build(tool: MAVEN, buildPath: null), delivery: upload, nexusDelivery: delivery)

        when:
        def config = written(service).toMap()

        then:
        reported(service) == []
        config.delivery == [
                push: [maven: [goals: ['deploy:deploy-file'],
                               flags: ['-DrepositoryId=bbh-snapshots', '-DgroupId=com.bbh.cert'],
                               artifacts: [[path: 'target/*.jar'], [path: 'target/cert-docs.zip', classifier: 'docs']]]],
                pull: [outputDirectory: 'nexus', group: 'com.bbh.cert', artifact: 'cert-gui',
                       maven: [goals: ['org.apache.maven.plugins:maven-dependency-plugin:3.6.1:get'],
                               flags: ['-DremoteRepositories=bbh::default::https://tools.bbh.com/nexus/repository/snapshots'],
                               artifacts: [[path: 'cert-gui.jar'], [path: 'cert-docs.zip', classifier: 'docs']]]]]
    }

    def "a Maven service on a VM without the artifacts of the delivery keeps the upload of delivery.maven"() {
        expect:
        written(settings(build: build(tool: MAVEN), delivery: command(['deploy:deploy-file'], ['-B']))).toMap().delivery ==
                [maven: [goals: ['deploy:deploy-file'], flags: ['-B']]]
    }

    def "a Gradle service on a VM uploads with delivery.push.gradle and passes its flags to the upload and the download"() {
        given:
        def delivery = NexusDeliverySettings.builder().artifacts(['build/libs/*.jar']).group('com.bbh.cert')
                .artifact('cert-api').repositoryUrl('http://nexus.bbh.com/repository/snapshots').allowInsecureProtocol(true)
                .pull(ToolCommand.builder().flags(['--offline']).build()).pullArtifacts(['cert-api.jar']).build()
        def service = settings(build: build(tool: GRADLE), nexusDelivery: delivery,
                delivery: ToolCommand.builder().flags(['--info']).environment(['GRADLE_OPTS=-Xmx1g']).build())

        when:
        def config = written(service).toMap()

        then:
        reported(service) == []
        config.delivery == [
                push: [gradle: [group: 'com.bbh.cert', artifact: 'cert-api',
                                repositoryUrl: 'http://nexus.bbh.com/repository/snapshots', allowInsecureProtocol: true,
                                flags: ['--info'], env: [GRADLE_OPTS: '-Xmx1g'], artifacts: [[path: 'build/libs/*.jar']]]],
                pull: [group: 'com.bbh.cert', artifact: 'cert-api',
                       gradle: [repositoryUrl: 'http://nexus.bbh.com/repository/snapshots', allowInsecureProtocol: true,
                                flags: ['--offline'], artifacts: [[path: 'cert-api.jar']]]]]
    }

    def "a Gradle service without the artifacts of the delivery renders no upload"() {
        expect:
        written(settings(build: build(tool: GRADLE))).toMap().delivery == null
    }

    def "the Nexus delivery needs what the library reads to upload and download: #fields"() {
        expect:
        reported(settings(build: build(tool: tool, buildPath: 'target/*.jar'), delivery: upload, nexusDelivery: delivery))*.field == fields

        where:
        tool   | upload                                            | delivery                                                                                                || fields
        MAVEN  | command(['deploy:deploy-file'], [])               | NexusDeliverySettings.builder().pullArtifacts(['cert.jar']).build()                                     || ['nexusDelivery.group', 'nexusDelivery.artifact', 'nexusDelivery.pull.tasks']
        GRADLE | NONE                                              | NexusDeliverySettings.builder().artifacts(['build/libs/*.jar']).build()                                 || ['nexusDelivery.group', 'nexusDelivery.artifact', 'nexusDelivery.repositoryUrl']
        GRADLE | ToolCommand.builder().flags(['--project-dir=x']).build() | NexusDeliverySettings.NONE                                                                        || ['delivery.flags']
        GRADLE | ToolCommand.builder().tasks(['publish']).build()  | NexusDeliverySettings.NONE                                                                              || ['delivery.tasks']
        MAVEN  | command(['deploy:deploy-file'], [])               | NexusDeliverySettings.builder().artifacts(['target/*.jar a b']).build()                                 || ['nexusDelivery.artifacts']
        MAVEN  | command(['deploy:deploy-file'], [])               | NexusDeliverySettings.builder().group('com.bbh').artifact('cert').pull(command(['get'], []))
                .pullArtifacts(['cert.jar', 'lib/cert.jar']).build()                                                                                                                       || ['nexusDelivery.pullArtifacts']
        MAVEN  | command(['deploy:deploy-file'], [])               | NexusDeliverySettings.builder().group('com.bbh').artifact('cert').pull(command(['get'], []))
                .pullArtifacts(['README']).build()                                                                                                                                         || ['nexusDelivery.pullArtifacts']
        GRADLE | NONE                                              | NexusDeliverySettings.builder().artifacts(['a.jar']).group('com bbh').artifact('cert')
                .repositoryUrl('ftp://nexus').build()                                                                                                                                      || ['nexusDelivery.group', 'nexusDelivery.repositoryUrl']
    }

    def "a service whose Nexus delivery is off is not checked for its delivery settings"() {
        expect:
        reported(settings(build: build(tool: GRADLE), deployment: deployment(nexusDeliveryDisabled: true),
                nexusDelivery: NexusDeliverySettings.builder().artifacts(['a.jar']).build())) == []
    }

    def "an OpenShift service needs an RD target that can build its image"() {
        given:
        def rd = OpenShiftTarget.builder().projectBuild('cert-build').dockerFilePath('Dockerfile').buildContext('.')
                .dockerRepoPush('push.bbh.com/cert').projectDeployment('cert-rd').build()

        when:
        def problems = reported(settings(deployment: deployment(target: OPENSHIFT, appName: 'cert',
                artifactName: 'cert.jar'), openShiftTargets: [(RD): rd]))

        then:
        problems*.field == ['openShiftTargets[RD].buildConfigPath', 'openShiftTargets[RD].nexusAuthFile']
        problems*.message.unique() ==
                ['is required for OpenShift: the Nexus snapshot delivery builds the image in the RD project']
    }

    def "a service that does not deliver to Nexus needs none of the fields only the delivery reads and renders delivery.nexusDeliveryEnabled on #target"() {
        given:
        def undelivered = settings(build: build(tool: tool), delivery: NONE,
                deployment: deployment(target: target, appName: 'cert', nexusDeliveryDisabled: true),
                flutter: FlutterSettings.builder().modules(['app']).testModules(['app']).signingPasswordCredentialsId('sign')
                        .prodLicenseCredentialsId('prod-license').testLicenseCredentialsId('test-license').build())

        expect:
        reported(undelivered) == []
        written(undelivered).get('delivery.nexusDeliveryEnabled') == false
        written(undelivered).get(legacy + '.nexusDeliveryEnabled') == null
        written(settings(deployment: deployment(target: target))).get('delivery.nexusDeliveryEnabled') == null

        where:
        tool    | target    || legacy
        MAVEN   | VM        || 'deploy.vm.rd'
        FLUTTER | VM        || 'deploy.vm.rd'
        GRADLE  | OPENSHIFT || 'deploy.openshift.rd'
    }

    def "an OpenShift QC target that sets any field needs what the QC deployment reads: #fields"() {
        expect:
        reported(settings(deployment: deployment(target: OPENSHIFT, appName: 'cert', artifactName: 'cert.jar'),
                openShiftTargets: [(RD): fullSettings().openShiftTargets()[RD], (QC): qc]))
                .collect { [it.field, it.message] } ==
                fields.collect { ["openShiftTargets[QC].$it" as String, 'is required once any QC field is set: the QC deployment uses it'] }

        where:
        qc                                                                                       || fields
        OpenShiftTarget.builder().buildTag('1.0.41').build()                                     || ['projectDeployment', 'deployConfigPath', 'dockerRepoPull', 'configPath']
        OpenShiftTarget.builder().projectDeployment('cert-qc').skipConfigDeploy(true).build()   || ['deployConfigPath', 'dockerRepoPull']
        OpenShiftTarget.builder().projectDeployment('cert-qc').deployConfigPath('oc/deploy.yaml')
                .dockerRepoPull('pull.bbh.com/cert').configPath('oc/config').build()             || []
        OpenShiftTarget.builder().routeHostname(' ').build()                                     || []
    }

    def "every Nexus IQ application of the service needs its name and scan patterns"() {
        expect:
        reported(settings(nexusIqApplications: applications))*.field == fields

        where:
        applications                                  || fields
        [NexusIqApplication.of(null, ['**/*.war'])]   || ['nexusIqApplications[0].application']
        [NexusIqApplication.of('cert', [])]           || ['nexusIqApplications[0].scanPatterns']
        [NexusIqApplication.of('cert', ['**/*.war'])] || []
        []                                            || []
    }

    def "an UrbanCode application needs components that name their folder and files"() {
        given:
        def applications = [UrbanCodeApplicationSettings.of('Cert', 1, [], null, []),
                            UrbanCodeApplicationSettings.of('Cert Batch', 2, [], null,
                                    [UrbanCodeComponent.of('batch', ' ', null),
                                     UrbanCodeComponent.of('config', 'config', '*.yml')])]

        expect:
        reported(settings(urbanCodeApplications: applications))*.field == ['urbanCodeApplications[0].components',
                                                                          'urbanCodeApplications[1].components[0].baseDir',
                                                                          'urbanCodeApplications[1].components[0].fileIncludePatterns']
    }

    def "test job parameters '#parameters' are one NAME=value per line: #valid"() {
        when:
        def problems = reported(settings(testJobs: [TestJob.builder().stage(SMOKE).type(LOCAL).job('CERT/smoke')
                .parameters(parameters).build()]))

        then:
        problems.collect { [it.field, it.message] } ==
                (valid ? [] : [['testJobs[0].parameters', 'write one parameter per line as NAME=value']])

        where:
        parameters                      || valid
        'ENV=rd\nSUITE=critical'        || true
        'ENV=rd\n\n  \nlog.level-x=a=b' || true
        'ENV=rd,SUITE=critical'         || true
        'ENV=rd\nSUITE critical'        || false
        '1ENV=rd'                       || false
        'ENV=rd\n =x'                   || false
        null                            || true
    }

    def "list values that do not fit their column are refused field by field"() {
        given:
        def longFlags = (1..12).collect { "-Dproperty.$it=${'v' * 200}".toString() }
        def longDirs = (1..20).collect { "${'d' * 150}/$it".toString() }
        def reviewers = (1..3).collect { ("reviewer-$it-" + '\u017c\u00f3\u0142\u0107' * 120).toString() }
        def command = ToolCommand.of(['build'], longFlags)
        def nodeBuild = NodeBuildSettings.builder().commands(longDirs + longDirs).environment(longFlags).build()

        when:
        def problems = reported(settings(build: build(command: command), nodeBuild: nodeBuild,
                appScan: AppScanSettings.builder().applicationId(APP_ID).includedDirs(longDirs)
                        .excludedDirs(['test']).compile(false).compileCommand(command).build(),
                scm: ScmSettings.builder().repositoryUrl(REPO).credentialsId('bb-creds').reviewers(reviewers).build(),
                goldenFix: GoldenFixPolicy.builder().excludeDirs(longDirs).build(), versionCatalogs: longDirs))

        then:
        problems*.field == ['build.command.flags', 'nodeBuild.commands', 'nodeBuild.environment', 'appScan.includedDirs',
                            'appScan.compileCommand.flags', 'scm.reviewers', 'goldenFix.excludeDirs', 'versionCatalogs']
        problems[0].message == 'is too long: all entries together may take at most 2000 bytes'
    }

    def "a remote test job given as a path must name its Jenkins"() {
        given:
        def jobs = [TestJob.of(SMOKE, null, REMOTE, 'CERT/smoke', null),
                    TestJob.builder().stage(SMOKE).type(REMOTE).job('https://jenkins.qc/job/smoke/').build(),
                    TestJob.builder().stage(REGRESSION).type(REMOTE).job('CERT/regression').remoteJenkins('qc').build()]

        expect:
        reported(settings(testJobs: jobs))*.field == ['testJobs[0].remoteJenkins']
    }

    def "a missing metrics project is filled in from the product code and the service name"() {
        given:
        def plain = settings()
        def explicit = settings(metrics: new MetricsSettings(false, 'cert-scanner', 'uat', null, null))

        expect:
        plain.withDefaultMetricsProject('CERT', 'gui').metrics() == new MetricsSettings(true, 'CERT-gui', 'test', null, null)
        plain.withDefaultMetricsProject('CERT', 'gui').build() == plain.build()
        explicit.withDefaultMetricsProject('CERT', 'gui').is(explicit)
    }

    def "a Liquibase service without database changes renders every section as on main: #target"() {
        expect:
        written(billingDb(target)).toMap() == expected
        written(billingDb(target)).toMap().keySet() as List == expected.keySet() as List

        where:
        target    || expected
        VM        || BILLING_DB_VM
        OPENSHIFT || BILLING_DB_VM.collectEntries { key, value -> key == 'appName' ? [appName: value,
                     artifactName: 'billing.zip'] : [key, value] } + [deployTarget: 'openshift',
                     deploy: [openshift: [qc: [projectDeploymentR: 'billing-qc']]]]
    }

    def "the changelog file adds the database block to a Liquibase service and leaves out its deployment"() {
        given:
        def database = DatabaseSettings.builder().changelogFile('db/changelog/db.changelog-master.xml').build()
        def plain = written(billingDb(VM)).toMap()

        when:
        def changes = written(billingDb(VM, database)).toMap()

        then:
        changes == plain.findAll { !(it.key in DEPLOYMENT) } + [database: [changelogFile: database.changelogFile()]]
        plain.keySet().containsAll(DEPLOYMENT)
    }

    def "a Liquibase service with database changes checks its database block and not its deployment"() {
        given:
        def deploys = [deployment: deployment(target: OPENSHIFT, artifactName: 'orders.war'), delivery: command([]),
                       urbanCodeApplications: [UrbanCodeApplicationSettings.of('Orders', 1, ['RD'], null, [])]]
        def deployment = reported(settings(deploys + [build: build(tool: LIQUIBASE, command: NONE)]))

        expect:
        reported(databaseSettings(deploys)) == []
        reported(databaseSettings(deploys + [database: DatabaseSettings.NONE, databaseTargets: [:]])) == deployment
        deployment*.field.containsAll(['deployment.appName', 'urbanCodeApplications[0].components'])
        reported(databaseSettings(database: database().toBuilder().changelogFile(null).build(),
                databaseTargets: [(DatabaseTarget.RD): DatabaseTargetSettings.builder().url('x').build()]))*.field ==
                ['database.changelogFile', 'databaseTargets[RD].url', 'databaseTargets[RD].credentialsId',
                 'database.utplsql']
        reported(databaseSettings(build: build())).collect { [it.field, it.message] } == [['database', 'is set for a'
                + ' service that does not build with Liquibase: only a Liquibase service, or one of SQL and PL/SQL'
                + ' files, changes a database']]
    }

    def "an npm, pip, Android and iOS service is valid and writes the keys the library reads for its build tool"() {
        given:
        def pip = settings(build: build(tool: BuildTool.PIP, javaPath: null, command: command(['pip', 'install', '-r', 'requirements.txt'])),
                unitTests: UnitTestSettings.builder().command(ToolCommand.builder().tasks(['pytest', '--junitxml=junit.xml'])
                        .directory('tests').build()).build(),
                sonar: SonarSettings.of(null, 'com.bbh:rates', ToolCommand.of([], ['-Dsonar.python.version=3.12'])))
        def node = NodeBuildSettings.builder().directory('web').commands(['npm ci', 'npm run build']).build()
        def npm = { Map changes = [:] ->
            settings([build: build(tool: BuildTool.NPM, javaPath: null, command: NONE), nodeBuild: node,
                      unitTests: UnitTestSettings.builder().command(command(['npm', 'test'])).build()] + changes)
        }
        def android = settings(build: build(tool: BuildTool.ANDROID, command: command(['clean', 'assembleRelease'])),
                sonar: SonarSettings.of(null, 'com.bbh:app', command(['sonarqube'])))
        def ios = settings(build: build(tool: BuildTool.IOS, javaPath: null, command: command(['xcodebuild', '-scheme', 'App', 'build'])))

        expect:
        reported(pip) == []
        reported(npm()) == []
        reported(android) == []
        reported(ios) == []
        with(written(pip).toMap()) {
            buildTool == 'pip'
            build.pip.commands == ['pip install -r requirements.txt']
            tests.unitTests.commands == ['pytest --junitxml=junit.xml']
            tests.unitTests.dir == 'tests'
            tools.sonar.pip.flags == ['-Dsonar.python.version=3.12']
            !containsKey('javaPath')
        }
        with(written(npm()).toMap()) {
            buildTool == 'npm'
            build.node == [dir: 'web', commands: ['npm ci', 'npm run build']]
            tests.unitTests.commands == ['npm test']
        }
        written(npm(nodeBuild: NodeBuildSettings.builder().enabled(true).directory('web').build())).toMap().build.node == [enabled: true, dir: 'web']
        written(npm(nodeBuild: NodeBuildSettings.NONE)).toMap().buildTool == 'npm'
        with(written(android).toMap()) {
            buildTool == 'android'
            build.gradle.tasks == ['clean', 'assembleRelease']
            tools.sonar.gradle.tasks == ['sonarqube']
        }
        written(ios).toMap().build.ios.commands == ['xcodebuild -scheme App build']
    }

    def "an npm, pip or iOS service refuses what the library would not read, and an Android service needs its Gradle tasks and a JDK"() {
        expect:
        reported(settings(build: build(tool: BuildTool.NPM, javaPath: null)))*.field == ['build.command.tasks']
        reported(settings(build: build(tool: BuildTool.PIP, javaPath: null, command: command(['pip', 'install', '.'], ['--quiet']))))*.field ==
                ['build.command.flags']
        reported(settings(build: build(tool: BuildTool.IOS, javaPath: null, command: NONE),
                sonar: SonarSettings.of(null, 'com.bbh:app', command(['sonarqube']))))*.field == ['sonar.command.tasks']
        reported(settings(build: build(tool: BuildTool.ANDROID, command: NONE)))*.field == ['build.command.tasks']
        reported(settings(build: build(tool: BuildTool.ANDROID, javaPath: null)))*.field == ['build.javaPath']
    }

    def "a service of SQL and PL/SQL files changes a database without a changelog file and without commands of its own"() {
        given:
        def files = database().toBuilder().changelogFile(null).searchPath('db').build()
        def sql = { Map changes = [:] ->
            databaseSettings([build: build(tool: BuildTool.SQL, command: NONE), database: files] + changes)
        }
        def config = written(sql()).toMap()

        expect:
        sql().changesDatabase()
        sql().deployTarget() == null
        reported(sql()) == []
        reported(sql(database: DatabaseSettings.NONE, databaseTargets: [:])) == []
        config.buildTool == 'sql'
        config.database.searchPath == 'db'
        !config.database.containsKey('changelogFile')
        !config.containsKey('build')
        !config.containsKey('deployTarget')
        reported(sql(database: database()))*.field == ['database.changelogFile']
        reported(sql(build: build(tool: BuildTool.SQL, command: command(['updateSQL']))))*.field == ['build.command.tasks']
        reported(sql(build: build(tool: BuildTool.SQL, command: NONE, javaPath: null)))*.field == ['build.javaPath']
    }

    def "a database that sets only the library's defaults is dropped, and the package named after the service too"() {
        given:
        def stored = databaseSettings(database: database().withArtifactId('orders-db'), databaseTargets: [
                (DatabaseTarget.TEST): DatabaseTargetSettings.builder().contexts('test').timeoutMinutes(30).build(),
                (DatabaseTarget.QC)  : DatabaseTargetSettings.builder().contexts('!test').build()])

        expect:
        stored.databaseTargets().keySet() as List == [DatabaseTarget.QC]
        stored.withDefaultArtifact('orders-db').database().artifactId() == null
        stored.withDefaultArtifact('orders').database().artifactId() == 'orders-db'
        stored.changesDatabase()
        stored.deployTarget() == null
        !settings().changesDatabase()
        settings().deployTarget() == VM
        settings().database() == DatabaseSettings.NONE
        settings().databaseTargets() == [:]
    }

    def "a service cannot do without its #section settings"() {
        when:
        ServiceSettings.builder().build(section == 'build' ? null : build())
                .deployment(section == 'deployment' ? null : deployment())
                .appScan(section == 'AppScan' ? null : appScan()).build()

        then:
        def e = thrown(NullPointerException)
        e.message == "a service needs its $section settings"

        where:
        section << ['build', 'deployment', 'AppScan']
    }

    /** The deployment keys that a Liquibase service with database changes leaves out. */
    static final List<String> DEPLOYMENT = ['deployTarget', 'appName', 'baseArtifactName', 'deploy']
    /** What main (a725366) renders for billingDb(VM), keys in order. */
    static final Map BILLING_DB_VM = [
            buildTool       : 'liquibase', sourceDir: 'db',
            build           : [buildPath: 'db/changelog/*.xml',
                               liquibase: [commands: ['updateSQL'],
                                           flags   : ['--changelog-file=db/changelog/db.changelog-master.xml']],
                               node     : [enabled: true, dir: 'web', commands: ['npm ci']]],
            deployTarget    : 'vm', appName: 'billing', baseArtifactName: 'billing-db', appId: APP_ID,
            asoc            : [liquibase: [commands: ['updateSQL']]],
            dast            : [enabled: true, targetUrl: 'https://billing.rd.bbh.com'],
            influx          : [enabled: true, env: 'test'],
            tests           : [unitTests: [liquibase: [commands: ['validate']], unitTestResult: '**/utplsql/*.xml'],
                               smoke    : [jobs: [[job: 'billing/smoke']]]],
            coverage        : [reportPath: 'target/coverage.xml'],
            tools           : [sonar  : [projectName: 'Billing-DB', projectKey: 'com.bbh:billing-db',
                                         liquibase  : [commands: ['status'], flags: ['-Dsonar.sources=db']]],
                               nexusIq: [application: 'billing-db', scanPatterns: ['**/*.jar'], stage: 'build',
                                         failOnNetworkError: false]],
            goldenFix       : [enabled: false, versionCatalogs: ['gradle/libs.versions.toml'],
                               versionCatalogRepo: [url: 'https://bitbucket.bbh.com/scm/ta/catalogs.git',
                                                    credentialsId: 'bb']],
            deploy          : [vm: [dod: [skipWait: false, deployWithSnapshot: true, updateSnapshotComp: false,
                                          includeOnlyDeployVersions: true, deployOnlyChanged: false,
                                          applications: [[applicationName: 'Cert', order: 1, environments: ['RD'],
                                                          components: [[componentName: 'cert-gui', baseDir: 'build',
                                                                        fileIncludePatterns: '*.war',
                                                                        incrementalVersion: false]]]]],
                                    rd : [host: 'rd.host']]]]

    /** The Liquibase service billing-db with every setting main renders for it, deployed to a VM or to OpenShift. */
    static ServiceSettings billingDb(DeployTarget target, DatabaseSettings database = null) {
        settings(build: new BuildSettings(LIQUIBASE, 'db', null, false, 'db/changelog/*.xml',
                command(['updateSQL'], ['--changelog-file=db/changelog/db.changelog-master.xml'])),
                nodeBuild: new NodeBuildSettings(true, 'web', ['npm ci'], null, null, [], null, null, null, null),
                unitTests: UnitTestSettings.builder().command(command(['validate'])).resultPattern('**/utplsql/*.xml')
                        .coverageReportPath('target/coverage.xml').build(),
                deployment: deployment(target: target, appName: 'billing', baseArtifactName: 'billing-db',
                        artifactName: target == VM ? null : 'billing.zip'),
                delivery: command(['publish']), urbanCodeApplications: [UCD_APP],
                sshTargets: [(RD): RD_HOST], openShiftTargets: [(QC): OpenShiftTarget.builder().projectDeployment('billing-qc')
                        .build()],
                appScan: appScan(compile: true, compileCommand: command(['updateSQL']), dastEnabled: true,
                        dastTargetUrl: 'https://billing.rd.bbh.com'),
                sonar: SonarSettings.of('Billing-DB', 'com.bbh:billing-db', command(['status'], ['-Dsonar.sources=db'])),
                nexusIqApplications: [new NexusIqApplication('billing-db', ['**/*.jar'], null, null)],
                goldenFix: GoldenFixPolicy.builder().enabled(false).build(), versionCatalogs: ['gradle/libs.versions.toml'],
                versionCatalogRepo: new VersionCatalogRepo('https://bitbucket.bbh.com/scm/ta/catalogs.git', 'bb', null,
                        null),
                testJobs: [TestJob.builder().stage(SMOKE).job('billing/smoke').build()], database: database)
    }

    private static ConfigTree written(ServiceSettings settings) {
        def tree = new ConfigTree()
        settings.writeTo(tree)
        tree
    }

    private static List reported(ServiceSettings settings) {
        def problems = new ValidationProblems()
        settings.validate(problems)
        problems.list()
    }
}
