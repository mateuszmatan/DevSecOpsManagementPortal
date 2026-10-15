package com.bbh.itss.dso.portal.domain.catalog

import com.bbh.itss.dso.portal.domain.shared.ConfigTree
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT
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
import static com.bbh.itss.dso.portal.support.Fixtures.appScan
import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.Fixtures.command
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
                new ServiceSettings(build(), UnitTestSettings.NONE, DEFAULTS, [], deployment(), NONE,
                        UrbanCodeSettings.DEFAULTS, [], [:], [:], appScan(), SonarSettings.NONE, NexusIqSettings.NONE,
                        null, ScmSettings.NONE, INHERITED, MetricsSettings.DEFAULTS, FlutterSettings.NONE)
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
                'is required for Maven on VMs: the Nexus delivery publishes the artifact found there'
        reported(settings(build: build(tool: FLUTTER, javaPath: null)))*.field ==
                ['build.javaPath', 'flutter.modules', 'flutter.testModules', 'flutter.signingPasswordCredentialsId',
                 'flutter.prodLicenseCredentialsId', 'flutter.testLicenseCredentialsId', 'flutter.deliveryGroup',
                 'flutter.deliveryArtifact', 'flutter.deliveryPlugin']
        reported(settings(build: build(tool: MAVEN), delivery: NONE,
                deployment: deployment(target: OPENSHIFT, appName: 'cert', artifactName: 'cert.jar'),
                openShiftTargets: [(RD): fullSettings().openShiftTargets()[RD]])) == []
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

        when:
        def problems = reported(settings(build: build(command: command),
                appScan: AppScanSettings.builder().applicationId(APP_ID).includedDirs(longDirs).excludedDirs(['test'])
                        .compile(false).compileCommand(command).build(),
                scm: ScmSettings.builder().repositoryUrl(REPO).credentialsId('bb-creds').reviewers(reviewers).build(),
                goldenFix: GoldenFixPolicy.builder().excludeDirs(longDirs).build()))

        then:
        problems*.field == ['build.command.flags', 'appScan.includedDirs', 'appScan.compileCommand.flags',
                            'scm.reviewers', 'goldenFix.excludeDirs']
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
