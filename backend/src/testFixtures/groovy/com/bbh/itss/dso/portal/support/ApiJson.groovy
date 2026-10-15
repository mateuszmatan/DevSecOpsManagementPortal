package com.bbh.itss.dso.portal.support

import static com.bbh.itss.dso.portal.support.CatalogFixtures.DEPARTMENT_ID
import static com.bbh.itss.dso.portal.support.Fixtures.JDK

final class ApiJson {

    static final String APP_ID = Fixtures.APP_ID

    private ApiJson() {
    }

    static Map product(Map overrides = [:]) {
        [code        : 'CERT',
         name        : 'CertScanner',
         departmentId: DEPARTMENT_ID,
         appScan     : [keyId: 'bbh_key-id', secretCredentialsId: 'hcl-app-scan-account'],
         services    : [service()]] + overrides
    }

    static Map service(Map overrides = [:]) {
        [name      : 'gui',
         build     : build(),
         deployment: [target: 'VM'],
         appScan   : [applicationId: APP_ID]] + overrides
    }

    static Map build(Map overrides = [:]) {
        [tool: 'GRADLE', javaPath: JDK, command: [tasks: ['clean', 'build']]] + overrides
    }

    static Map mavenService(Map overrides = [:]) {
        service([build   : build(tool: 'MAVEN', buildPath: 'target/*.jar', command: [tasks: ['clean', 'verify']]),
                 delivery: [tasks: ['deploy:deploy-file']]] + overrides)
    }

    static Map fullMavenService(Map overrides = [:]) {
        [name                 : 'ledger',
         description          : 'Ledger postings',
         build                : [tool     : 'MAVEN', sourceDir: 'ledger', javaPath: JDK, autoSetup: false,
                                 buildPath: '/opt/maven/bin',
                                 command  : command(['clean', 'verify'], ['-B', '-DskipITs'], directory: 'ledger',
                                         mavenHome: '/opt/maven', environment: ['MAVEN_OPTS=-Xmx1g'],
                                         label: 'Ledger build')],
         unitTests            : [command           : command(['test'], ['-Pcoverage']),
                                 resultPattern     : '**/surefire-reports/*.xml', rootDir: 'ledger',
                                 reportOutDir      : 'target/unit-reports', allowEmptyResults: true,
                                 coverageReportPath: 'target/site/jacoco/jacoco.xml'],
         tests                : [maxParallel         : 4, smokeMaxParallel: 2, regressionMaxParallel: 3,
                                 performanceMaxParallel: 1, smokeRequired: true, regressionRequired: true,
                                 performanceRequired : false, smokePollIntervalSec: null, regressionPollIntervalSec: 30,
                                 performancePollIntervalSec: null],
         testJobs             : [[stage           : 'SMOKE', name: 'Ledger smoke', type: 'LOCAL', job: 'ledger/smoke-tests',
                                  timeoutMinutes  : 15, parameters: 'TARGET_ENV=uat', remoteJenkins: null,
                                  remoteJenkinsUrl: null, credentialsId: null] + remoteOptions(),
                                 [stage           : 'PERFORMANCE', name: 'Ledger load', type: 'REMOTE',
                                  job             : 'performance/ledger-load', timeoutMinutes: 120, parameters: null,
                                  remoteJenkins   : 'perf-jenkins', remoteJenkinsUrl: 'https://perf-jenkins.bbh.com',
                                  credentialsId   : 'perf-jenkins-token'] + remoteOptions(pollIntervalSec: 20,
                                         tokenCredentialsId: 'perf-jenkins-trigger', trustAllCertificates: true,
                                         useCrumbCache: true)],
         deployment           : [target: 'VM', appName: 'ledger', artifactName: 'ledger.war', baseArtifactName: 'ledger'],
         delivery             : command(['deploy:deploy-file'], ['-DrepositoryId=bbh-snapshots']),
         urbanCode            : [siteName                : 'deploy.bbh.com', deployProcess: 'tomcat-app-process',
                                 skipWait                : true, deployWithSnapshot: false, updateSnapshotComponents: true,
                                 includeOnlyDeployVersions: false, deployOnlyChanged: true,
                                 deployDescription       : 'Ledger release', requestProperties: 'restart=true'],
         urbanCodeApplications: [[applicationName: 'LEDGER', order: 1, environments: ['RD-UAT', 'QC-UAT'],
                                  snapshotName   : 'ledger-snapshot', siteName: null, deployProcess: 'ledger-process',
                                  skipWait       : false, deployWithSnapshot: null, updateSnapshotComponents: null,
                                  includeOnlyDeployVersions: null, deployOnlyChanged: null,
                                  deployDescription: 'Ledger to UAT', description: null, requestProperties: null,
                                  components     : [[componentName      : 'ledger-war', baseDir: 'target',
                                                     fileIncludePatterns: '*.war', fileExcludePatterns: '*-sources.war',
                                                     versionPrefix      : '1.4.', version: null, incrementalVersion: true,
                                                     extensions         : 'war', charset: 'UTF-8', pushDescription: null,
                                                     versionProperties  : null, versionDescription: 'Ledger build']]]],
         sshTargets           : [RD: [host        : 'rdltaapps1.testbbh.com', user: 'taadmin', deployDir: '/opt/ledger',
                                      deployScript: 'scripts/deploy.sh', versionFile: 'version.properties'],
                                 QC: [host        : 'qcltaapps1.testbbh.com', user: 'taadmin', deployDir: '/opt/ledger',
                                      deployScript: 'scripts/deploy.sh', versionFile: 'version.properties']],
         openShiftTargets     : [:],
         appScan              : [applicationId : APP_ID, sastScanName: 'ledger-sast', includedDirs: ['ledger/src/main'],
                                 excludedDirs  : ['ledger/src/test'], compile: true, sourceCodeOnly: false,
                                 useConfigFile : false, insecureTls: false, clientPath: '/opt/saclient',
                                 compileCommand: command(['compile'], ['-q']),
                                 dastEnabled   : true, dastScanName: 'ledger-dast',
                                 dastTargetUrl : 'https://ledger-uat.testbbh.com', dastPresenceId: 'presence-1',
                                 secretCredentialsId: 'ledger-appscan-secret'],
         sonar                : [projectName           : 'Ledger', projectKey: 'ledger', installationName: 'SonarQube',
                                 credentialsId         : 'sonar-user', authTokenCredentialsId: 'sonar-token',
                                 badgeToken            : 'sqb_1a2b3c', addBadges: true, fullBadges: false,
                                 serverUrl             : 'https://sonar-ledger.bbh.com',
                                 command               : command(['sonar:sonar'], ['-Dsonar.branch.name=develop'])],
         nexusIq              : [serverUrl: null, credentialsId: 'ledger-iq', scaScanName: 'ledger-sca'],
         nexusIqApplications  : [[application: 'ledger', scanPatterns: ['**/target/*.war'], stage: 'stage-release',
                                  failOnNetworkError: true]],
         scm                  : [repositoryUrl: 'https://bitbucket.bbh.com/projects/LED/repos/ledger',
                                 credentialsId: 'bitbucket-http-credentials', authType: 'BEARER', type: 'SERVER',
                                 targetBranch : 'develop', cloneUrl: 'https://bitbucket.bbh.com/scm/led/ledger.git',
                                 reviewers    : ['jdoe', 'asmith']],
         goldenFix            : [enabled           : true, onlyDirectDependencies: false, minThreatLevel: 4,
                                 ecosystems        : ['maven'], goldenVersionTypes: ['recommended-non-breaking'],
                                 excludeDirs       : ['legacy'], verifyEnabled: true, verifyMaxAttempts: 2,
                                 verifyTimeoutMinutes: 30, verifyMavenCommand: 'mvn -B verify',
                                 verifyGradleCommand: null, verifyNpmCommand: null, verifyPipCommand: null,
                                 verifyPubCommand  : null, commitAuthorName: 'Ledger Team',
                                 commitAuthorEmail : 'ledger-team@bbh.com', timeZone: 'America/New_York'],
         metrics              : [enabled    : true, influxProject: 'ledger', influxEnv: 'uat',
                                 influxUrl  : 'https://influx-ledger.bbh.com/api/v2/write',
                                 influxCredentialsId: 'ledger-influx']] + overrides
    }

    static Map fullOpenShiftService(Map overrides = [:]) {
        [name            : 'ledger-api',
         build           : [tool: 'GRADLE', sourceDir: 'api', javaPath: JDK, autoSetup: false, buildPath: null,
                            command: command(['clean', 'bootJar'], ['--no-daemon'])],
         deployment      : [target: 'OPENSHIFT', appName: 'ledger-api', artifactName: 'ledger-api.jar',
                            baseArtifactName: null],
         openShiftTargets: [RD: openShiftTarget('ledger-rd'), QC: openShiftTarget('ledger-qc')],
         appScan         : [applicationId: APP_ID]] + overrides
    }

    static Map fullFlutterService(Map overrides = [:]) {
        [name      : 'ledger-mobile',
         build     : [tool: 'FLUTTER', sourceDir: 'mobile', javaPath: JDK, autoSetup: false, buildPath: null,
                      command: command([], [])],
         deployment: [target: 'VM', appName: null, artifactName: null, baseArtifactName: null],
         appScan   : [applicationId: APP_ID],
         flutter   : [platform                    : 'APK', modules: ['app'], testModules: ['app', 'core'],
                      testSubmodules              : ['core/model'], testSubplugins: ['plugins/camera'],
                      signingPasswordCredentialsId: 'flutter-signing', prodLicenseCredentialsId: 'flutter-prod-license',
                      testLicenseCredentialsId    : 'flutter-test-license', deliveryGroup: 'com.bbh.ledger',
                      deliveryArtifact            : 'ledger-mobile', deliveryPlugin: 'ledger-plugin',
                      sonarSources                : 'lib', sonarTests: 'test', sonarFlutterPlugin: true,
                      dartAnalyzeCommand          : 'dart analyze --fatal-infos', sonarScannerVersion: '5.0.1.3006']] + overrides
    }

    static Map command(Map options = [:], List<String> tasks, List<String> flags) {
        [tasks: tasks, flags: flags, directory: options.directory, mavenHome: options.mavenHome,
         environment: options.environment ?: [], label: options.label, returnStdout: options.returnStdout ?: false]
    }

    static Map remoteOptions(Map options = [:]) {
        [pollIntervalSec        : null, tokenCredentialsId: null, abortTriggeredJob: false,
         overrideTrustAllCertificates: false, preventRemoteBuildQueue: false, trustAllCertificates: false,
         useCrumbCache          : false, useJobInfoCache: false] + options
    }

    static Map openShiftTarget(String project) {
        [projectBuild       : "$project-build".toString(), buildConfigPath: 'openshift/build.yaml',
         dockerFilePath     : 'Dockerfile', buildContext: '.', addFile: 'target/app.jar',
         dockerRepoPush     : "registry.bbh.com/$project".toString(), dockerRepoPull: "registry.bbh.com/$project".toString(),
         certDir            : '/etc/certs', nexusAuthFile: '/etc/nexus/auth.json', projectDeployment: project,
         deployConfigPath   : 'openshift/deploy.yaml', configPath: 'openshift/config', skipConfigDeploy: true,
         healthCheckUrl     : '/actuator/health',
         routeHostname      : "${project}.apps.bbh.com".toString(), deploymentPath: 'k8s',
         deploymentRepoUrl  : "https://bitbucket.bbh.com/scm/deploy/${project}.git".toString(), deploymentRepoBranch: 'main',
         deploymentRepoCredentialsId: 'bitbucket-http-credentials', buildTag: null, internalDockerUrl: null]
    }

    static Map pipeline(Map overrides = [:]) {
        [type: 'FULL', agentLabels: ['linux-agent']] + overrides
    }
}
