package com.bbh.itss.dso.portal.support

import com.bbh.itss.dso.portal.domain.catalog.AppScanAccount
import com.bbh.itss.dso.portal.domain.catalog.AppScanSettings
import com.bbh.itss.dso.portal.domain.catalog.BuildSettings
import com.bbh.itss.dso.portal.domain.catalog.BuildTool
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget
import com.bbh.itss.dso.portal.domain.catalog.DeploymentSettings
import com.bbh.itss.dso.portal.domain.catalog.FlutterSettings
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings
import com.bbh.itss.dso.portal.domain.catalog.NexusIqApplication
import com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings
import com.bbh.itss.dso.portal.domain.catalog.OpenShiftTarget
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails
import com.bbh.itss.dso.portal.domain.catalog.ProductDirectory
import com.bbh.itss.dso.portal.domain.catalog.ProductDirectory.ProductIdentity
import com.bbh.itss.dso.portal.domain.catalog.Region
import com.bbh.itss.dso.portal.domain.catalog.ScmSettings
import com.bbh.itss.dso.portal.domain.catalog.Service
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings
import com.bbh.itss.dso.portal.domain.catalog.SshTarget
import com.bbh.itss.dso.portal.domain.catalog.TestJob
import com.bbh.itss.dso.portal.domain.catalog.TestSettings
import com.bbh.itss.dso.portal.domain.catalog.ToolCommand
import com.bbh.itss.dso.portal.domain.catalog.UnitTestSettings
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeApplicationSettings
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeComponent
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeSettings
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.pipeline.PipelineKey
import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.pipeline.ServiceRef
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType.BEARER
import static com.bbh.itss.dso.portal.domain.catalog.BitbucketType.SERVER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM
import static com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform.APPBUNDLE
import static com.bbh.itss.dso.portal.domain.catalog.Region.QC
import static com.bbh.itss.dso.portal.domain.catalog.Region.RD
import static com.bbh.itss.dso.portal.domain.catalog.TestJobType.LOCAL
import static com.bbh.itss.dso.portal.domain.catalog.TestJobType.REMOTE
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.PERFORMANCE
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.REGRESSION
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.SMOKE
import static com.bbh.itss.dso.portal.domain.catalog.ToolCommand.NONE
import static com.bbh.itss.dso.portal.domain.pipeline.KeyStatus.ACTIVE
import static com.bbh.itss.dso.portal.domain.pipeline.KeyStatus.REVOKED
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues.bbhDefaults
import static com.bbh.itss.dso.portal.support.CatalogFixtures.DEPARTMENT_ID
import static com.bbh.itss.dso.portal.support.CatalogFixtures.details

final class Fixtures {

    static final String APP_ID = '109f44ac-cc06-4ca0-884e-d944904f7019'
    static final String JDK = '/usr/lib/jvm/java-17-openjdk'
    static final String KEY = '0f8fad5b-d9cb-469f-a165-70867728950e'
    static final Instant CREATED = Instant.parse('2026-10-01T08:00:00Z')
    static final Instant UPDATED = Instant.parse('2026-10-02T09:30:00Z')

    private Fixtures() {
    }

    static ToolCommand command(List<String> tasks, List<String> flags = []) {
        ToolCommand.of(tasks, flags)
    }

    static BuildSettings build(Map args = [:]) {
        BuildTool tool = args.tool as BuildTool ?: GRADLE
        ToolCommand defaultCommand = tool == FLUTTER ? NONE
                : tool == MAVEN ? command(['clean', 'verify']) : command(['clean', 'build'])
        new BuildSettings(tool, args.sourceDir as String,
                args.containsKey('javaPath') ? args.javaPath as String : JDK, args.autoSetup as Boolean,
                args.buildPath as String, args.containsKey('command') ? args.command as ToolCommand : defaultCommand)
    }

    static DeploymentSettings deployment(Map args = [:]) {
        new DeploymentSettings(args.target as DeployTarget ?: VM, args.appName as String,
                args.artifactName as String, args.baseArtifactName as String)
    }

    static AppScanSettings appScan(Map args = [:]) {
        AppScanSettings.builder().applicationId(args.applicationId as String ?: APP_ID)
                .sastScanName(args.sastScanName as String).includedDirs(args.includedDirs as List<String>)
                .excludedDirs(args.excludedDirs as List<String>).compile(args.compile as Boolean)
                .sourceCodeOnly(args.sourceCodeOnly as Boolean).useConfigFile(args.useConfigFile as Boolean)
                .insecureTls(args.insecureTls as Boolean).clientPath(args.clientPath as String)
                .compileCommand(args.compileCommand as ToolCommand).dastEnabled(args.dastEnabled as Boolean)
                .dastScanName(args.dastScanName as String).dastTargetUrl(args.dastTargetUrl as String)
                .dastPresenceId(args.dastPresenceId as String).secretCredentialsId(args.secretCredentialsId as String)
                .build()
    }

    static ServiceSettings settings(Map args = [:]) {
        BuildSettings build = args.build as BuildSettings ?: build()
        DeploymentSettings deployment = args.deployment as DeploymentSettings ?: deployment()
        ToolCommand delivery = args.containsKey('delivery') ? args.delivery as ToolCommand
                : build.tool() == MAVEN && deployment.target() == VM
                ? command(['deploy:deploy-file']) : null
        ServiceSettings.builder().build(build).unitTests(args.unitTests as UnitTestSettings)
                .tests(args.tests as TestSettings).testJobs(args.testJobs as List<TestJob>).deployment(deployment)
                .delivery(delivery).urbanCode(args.urbanCode as UrbanCodeSettings)
                .urbanCodeApplications(args.urbanCodeApplications as List<UrbanCodeApplicationSettings>)
                .sshTargets(args.sshTargets as Map<Region, SshTarget>)
                .openShiftTargets(args.openShiftTargets as Map<Region, OpenShiftTarget>)
                .appScan(args.appScan as AppScanSettings ?: appScan()).sonar(args.sonar as SonarSettings)
                .nexusIq(args.nexusIq as NexusIqSettings)
                .nexusIqApplications(args.nexusIqApplications as List<NexusIqApplication>)
                .scm(args.scm as ScmSettings).goldenFix(args.goldenFix as GoldenFixPolicy)
                .metrics(args.metrics as MetricsSettings).flutter(args.flutter as FlutterSettings)
                .build()
    }

    static ServiceSettings fullSettings(String name = 'gui') {
        ServiceSettings.builder()
                .build(new BuildSettings(MAVEN, 'app', JDK, true, 'target/*.war', fullCommand('build')))
                .unitTests(new UnitTestSettings(fullCommand('test'), '**/TEST-*.xml', 'app', 'reports', true,
                        'target/jacoco.xml'))
                .tests(TestSettings.builder().maxParallel(5).smokeMaxParallel(1).regressionMaxParallel(2)
                        .performanceMaxParallel(3).regressionRequired(false).smokePollIntervalSec(10)
                        .performancePollIntervalSec(30).build())
                .testJobs([
                        TestJob.builder().stage(SMOKE).name('smoke').type(LOCAL).job("CERT/${name}-smoke")
                                .timeoutMinutes(10).parameters('A=1').build(),
                        TestJob.builder().stage(REGRESSION).name('regression').type(REMOTE).job('CERT/regression')
                                .timeoutMinutes(60).parameters('B=2\nC=3').remoteJenkins('jenkins-qc')
                                .remoteJenkinsUrl('https://jenkins-qc.bbh.com').credentialsId('remote-token')
                                .pollIntervalSec(20).tokenCredentialsId('remote-trigger-token').abortTriggeredJob(true)
                                .preventRemoteBuildQueue(true).useCrumbCache(true).build(),
                        TestJob.builder().stage(PERFORMANCE).job('https://jenkins-qc.bbh.com/job/load/').build()])
                .deployment(new DeploymentSettings(VM, "cert-${name}", "cert-${name}.war", 'cert-base'))
                .delivery(fullCommand('delivery'))
                .urbanCode(new UrbanCodeSettings('BBH-RD', 'Deploy', true, false, true, false, true, 'release', 'a=b'))
                .urbanCodeApplications([
                        UrbanCodeApplicationSettings.builder().applicationName('Cert').order(1)
                                .environments(['RD', 'QC']).snapshotName('snap').siteName('BBH-QC')
                                .deployProcess('Deploy QC').skipWait(false).updateSnapshotComponents(true)
                                .deployOnlyChanged(false).deployDescription('cert release').description('cert')
                                .requestProperties('c=d')
                                .components([
                                        new UrbanCodeComponent('cert-gui', 'build/libs', '*.war', '*.tmp', 'v', '1.0',
                                                true, 'war', 'UTF-8', 'gui push', 'build=1', 'gui version'),
                                        UrbanCodeComponent.builder().componentName('cert-config').baseDir('config')
                                                .fileIncludePatterns('*.yml').incrementalVersion(false).build()])
                                .build(),
                        UrbanCodeApplicationSettings.of('Cert Batch', 2, [], null,
                                [UrbanCodeComponent.of('cert-batch', 'batch/build', '*.jar')])])
                .sshTargets([(RD): new SshTarget('rd.host', 'dsoadm', '/opt/rd', 'deploy.sh', 'version.txt'),
                             (QC): SshTarget.builder().host('qc.host').build()])
                .openShiftTargets([
                        (RD): new OpenShiftTarget('cert-build', 'oc/build.yaml', 'Dockerfile', '.', 'add.txt',
                                'push.bbh.com/cert', 'pull.bbh.com/cert', 'certs', 'auth.json', 'cert-rd',
                                'oc/deploy.yaml', 'oc/config', true, '/health', 'cert.apps.bbh.com', 'deploy',
                                'https://bitbucket.bbh.com/scm/ta/deploy.git', 'main', 'deploy-creds', '1.0.42',
                                'image-registry.openshift-image-registry.svc:5000/cert'),
                        (QC): OpenShiftTarget.builder().projectDeployment('cert-qc').buildTag('1.0.41').build()])
                .appScan(AppScanSettings.builder().applicationId(APP_ID).sastScanName('Cert scan')
                        .includedDirs(['src']).excludedDirs(['test']).compile(false).sourceCodeOnly(true)
                        .useConfigFile(true).insecureTls(true).clientPath('/opt/appscan')
                        .compileCommand(fullCommand('compile')).dastEnabled(true).dastScanName('Cert DAST')
                        .dastTargetUrl('https://cert.testbbh.com').dastPresenceId('presence-1')
                        .secretCredentialsId('cert-appscan-secret').build())
                .sonar(new SonarSettings('CertScanner', "cert-${name}", 'SonarQube BBH', 'sonar-creds', 'sonar-token',
                        'badge', true, true, fullCommand('sonar'), 'https://sonar.cert.bbh.com'))
                .nexusIq(new NexusIqSettings('https://iq.cert.bbh.com', 'cert-iq', 'Cert SCA'))
                .nexusIqApplications([new NexusIqApplication("cert-${name}", ['**/*.war', '**/*.jar'], 'release', true),
                                      NexusIqApplication.of("cert-${name}-batch", ['**/batch/*.jar'])])
                .scm(new ScmSettings('https://bitbucket.bbh.com/scm/ta/cert.git', 'bb-creds', BEARER, SERVER, 'develop',
                        'ssh://git@bitbucket.bbh.com/ta/cert.git', ['alice', 'bob'],
                        'https://bitbucket.bbh.com/rest/api/1.0', 'ta-workspace', 'TA', 'cert-' + name))
                .goldenFix(new GoldenFixPolicy(false, true, 7, ['maven', 'npm'], ['recommended-non-breaking'],
                        ['docs', 'tests'], true, 3, 30, 'mvn verify', 'gradle check', 'npm test', 'pytest',
                        'flutter test', 'GoldenFix Bot', 'goldenfix@bbh.com', 'Europe/Warsaw'))
                .metrics(new MetricsSettings(false, "cert-${name}", 'qc', 'https://influx.cert.bbh.com/api/v2/write',
                        'cert-influx'))
                .flutter(new FlutterSettings(APPBUNDLE, ['core', 'app'], ['core'], ['core/sub'], ['plugin'], 'signing',
                        'prod-licence', 'test-licence', 'com.bbh', 'cert-mobile', 'deploy:deploy-file', 'lib', 'test',
                        true, 'dart analyze', '5.0'))
                .build()
    }

    static ToolCommand fullCommand(String prefix) {
        ToolCommand.builder().tasks([prefix + '-task', 'second']).flags(['--' + prefix]).directory(prefix)
                .mavenHome('/opt/' + prefix + '/maven').environment([prefix.toUpperCase() + '_OPTS=-Xmx1g', 'CI=true'])
                .label(prefix.capitalize() + ' step').returnStdout(prefix == 'build').build()
    }

    static AppScanAccount account() {
        new AppScanAccount('bbh_key-id', 'hcl-app-scan-account')
    }

    static ServiceDraft draft(Map args = [:]) {
        new ServiceDraft(args.id as Long, args.name as String ?: 'gui', args.description as String,
                args.settings as ServiceSettings ?: settings(args))
    }

    static Service service(Map args = [:]) {
        new Service(args.id as Long, args.name as String ?: 'gui', args.description as String,
                (args.displayOrder ?: 0) as int, args.settings as ServiceSettings ?: settings(args))
    }

    static Product product(Map args = [:]) {
        ProductDetails details = details(args)
        List<Map> services = args.containsKey('services') ? args.services as List<Map> : []
        List<Service> stored = services.withIndex().collect { Map entry, int order ->
            Service plain = service(entry + [displayOrder: entry.displayOrder ?: order])
            new Service(plain.id(), plain.name(), plain.description(), plain.displayOrder(),
                    plain.settings().withDefaultMetricsProject(details.code(), plain.name()))
        }
        Product.restore(args.containsKey('id') ? args.id as Long : 1L, details,
                args.containsKey('appScan') ? args.appScan as AppScanAccount : account(),
                stored, (args.version ?: 0) as long, CREATED, UPDATED)
    }

    static PipelineSettings pipelineSettings(Map args = [:]) {
        new PipelineSettings(args.agentLabels as List<String> ?: ['linux-agent'], args.extendedPipelineJob as String,
                args.securityPipelineJob as String, args.jenkinsJob as String, args.description as String)
    }

    static PipelineKey activeKey(Map args = [:]) {
        PipelineKey.builder().id((args.id ?: 100L) as Long).value(args.value as String ?: KEY).status(ACTIVE)
                .issuedAt(args.issuedAt as Instant ?: CREATED).lastUsedAt(args.lastUsedAt as Instant).build()
    }

    static PipelineKey revokedKey(Map args = [:]) {
        PipelineKey.builder().id((args.id ?: 99L) as Long)
                .value(args.value as String ?: '6ba7b810-9dad-41d1-80b4-00c04fd430c8').status(REVOKED)
                .issuedAt(args.issuedAt as Instant ?: CREATED).revokedAt(args.revokedAt as Instant ?: UPDATED)
                .revokeReason(args.containsKey('reason') ? args.reason as String : 'Replaced by a new key').build()
    }

    static Pipeline pipeline(Map args = [:]) {
        Pipeline.restore(args.containsKey('id') ? args.id as Long : 20L,
                new ServiceRef((args.productId ?: 1L) as long, (args.serviceId ?: 10L) as long),
                args.type as PipelineType ?: FULL, pipelineSettings(args),
                args.containsKey('keys') ? args.keys as List<PipelineKey> : [activeKey()], (args.version ?: 0) as long,
                CREATED, UPDATED)
    }

    static ProductDirectory directory(Map args = [:]) {
        new ProductDirectory() {
            @Override
            Optional<ProductIdentity> findProductByCode(String code) {
                Optional.ofNullable((args.byCode as Map)?.get(code) as ProductIdentity)
            }

            @Override
            Optional<ProductIdentity> findProductByName(String name) {
                Optional.ofNullable((args.byName as Map)?.get(name) as ProductIdentity)
            }

            @Override
            boolean departmentExists(long id) {
                id in (args.departments ?: [DEPARTMENT_ID])
            }
        }
    }

    static GlobalSettingsValues globalSettings(Closure<GlobalSettingsValues> change = { it }) {
        change(bbhDefaults())
    }

    static GlobalSettings storedSettings(Closure<GlobalSettingsValues> change = { it }) {
        new GlobalSettings(globalSettings(change), 1, Instant.parse('2026-10-04T12:00:00Z'))
    }

    static GlobalSettings storedSettings(String jenkinsUrl) {
        storedSettings { it.withPlatform(it.platform().withJenkinsUrl(jenkinsUrl)) }
    }

}
