import { Field, check, choice, count, mono } from '@common/shared/fields';
import { SHELL_SAFE_ERROR, SHELL_SAFE_URL_ERROR } from '@common/shared/form-controls';
import { GRADLE_MAVEN_FLUTTER, VM_OPENSHIFT } from '../shared/build-options';
import { SettingsSectionId } from './settings-form-model';

interface SettingsBlock {
  heading: string;
  note: string;
  fields: Field[];
}

interface SettingsSection {
  id: SettingsSectionId;
  label: string;
  note: string;
  fields?: Field[];
  blocks?: SettingsBlock[];
}

const HOST_ERROR = 'Use letters, digits, dots and hyphens';

const minutes = (key: string, label: string, code: string, span = 4): Field =>
  count(key, label, code, span, { min: 1 });

export const SETTINGS_PAGE: SettingsSection[] = [
  {
    id: 'platform',
    label: 'Tools and servers',
    note:
      'The addresses of the BBH tools every pipeline talks to. They change only when a tool ' +
      'moves, and the DevSecOps team then gives you the new value. Credentials are the names of ' +
      'Jenkins credentials, never passwords.',
    blocks: [
      {
        heading: 'Jenkins',
        note: 'Where the pipelines run, and the shared library their Jenkinsfiles load.',
        fields: [
          mono('jenkinsUrl', 'Jenkins URL', 'platform.jenkinsUrl', 7, {
            placeholder: 'https://jenkins.bbh.com/',
            hint: 'The portal links the Jenkins job of each pipeline from it',
          }),
          mono('jenkinsLibrary', 'Shared library', 'platform.jenkinsLibrary', 5, {
            hint: 'The name every Jenkinsfile loads with `@Library`',
          }),
        ],
      },
      {
        heading: 'HCL AppScan',
        note: 'The service that runs the security scans, the scan clients the pipelines download and the proxy they reach AppScan through.',
        fields: [
          mono('asocUrl', 'AppScan on Cloud URL', 'asoc.url', 12, {
            placeholder: 'https://cloud.appscan.com',
            hint: 'The address of the AppScan service, also sent as `APPSCAN_SERVER_URL`',
          }),
          mono('appScanClientLinuxUrl', 'Scan client download for Linux', 'SA_LINUX_URL', 6),
          mono('appScanClientWindowsUrl', 'Scan client download for Windows', 'SA_WIN_URL', 6),
          mono('proxyHost', 'Proxy host', 'PROXY_HOST', 5, {
            hint: 'Set together with the port, or leave both empty',
            error: HOST_ERROR,
          }),
          {
            key: 'proxyPort',
            label: 'Proxy port',
            code: 'PROXY_PORT',
            span: 3,
            kind: 'number',
            min: 1,
            max: 65535,
          },
          mono('proxyUser', 'Proxy user', 'PROXY_USER', 4),
          mono('oisHost', 'OIS host', 'platform.oisHost', 6, { error: HOST_ERROR }),
        ],
      },
      {
        heading: 'SonarQube and Nexus IQ',
        note: 'The servers that check the code quality (SonarQube) and the open source libraries (Nexus IQ) of every service.',
        fields: [
          mono('sonarServerUrl', 'SonarQube server URL', 'tools.sonar.serverUrl', 7),
          mono(
            'sonarInstallationName',
            'SonarQube installation',
            'tools.sonar.installationName',
            5,
            { hint: 'Its name in the Jenkins settings' },
          ),
          mono('nexusIqServerUrl', 'Nexus IQ server URL', 'tools.nexusIq.serverUrl', 7),
          mono(
            'nexusIqCredentialsId',
            'Nexus IQ credentials ID',
            'tools.nexusIq.credentialsId',
            5,
            { hint: 'The Jenkins credentials the pipelines sign in with' },
          ),
        ],
      },
      {
        heading: 'Repository, metrics and agents',
        note: 'The Nexus repository of snapshot builds, the InfluxDB where the pipelines store their results for Pipeline Monitoring, and the machine iOS apps are built on.',
        fields: [
          mono(
            'nexusSnapshotRepositoryUrl',
            'Nexus snapshot repository URL',
            'platform.nexusSnapshotRepositoryUrl',
            7,
            { error: SHELL_SAFE_URL_ERROR },
          ),
          mono(
            'nexusSnapshotRepositoryId',
            'Nexus snapshot repository ID',
            'platform.nexusSnapshotRepositoryId',
            5,
            { error: SHELL_SAFE_ERROR },
          ),
          mono('influxWriteUrl', 'InfluxDB write URL', 'influx.url', 7, {
            hint: 'Where each run stores its results',
          }),
          mono('influxCredentialsId', 'InfluxDB credentials ID', 'influx.credentialsId', 5, {
            hint: 'The Jenkins credentials that hold the InfluxDB token',
          }),
          mono('iosBuildAgent', 'iOS build agent', 'platform.iosBuildAgent', 6, {
            hint: 'The Jenkins agent label of the machine iOS apps are built on',
          }),
        ],
      },
    ],
  },
  {
    id: 'deployment',
    label: 'Deployment defaults',
    note:
      'How a service that runs on virtual machines is deployed to the test regions, unless the ' +
      'service sets its own values. These change only when the test servers or the UrbanCode ' +
      'setup change.',
    blocks: [
      {
        heading: 'UrbanCode Deploy',
        note: 'The UrbanCode site and process that deploy a service.',
        fields: [
          mono('urbanCodeSiteName', 'Site name', 'deploy.vm.dod.siteName', 6),
          mono('urbanCodeDeployProcess', 'Deploy process', 'deploy.vm.dod.deployProcess', 6),
        ],
      },
      {
        heading: 'Test servers',
        note: 'The servers of the two test regions and how the deployment script runs on them over SSH.',
        fields: [
          mono('rdHost', 'RD test server', 'deploy.vm.rd.host', 6, {
            hint: 'The lower test region',
            error: HOST_ERROR,
          }),
          mono('qcHost', 'QC test server', 'deploy.vm.qc.host', 6, {
            hint: 'The higher test region',
            error: HOST_ERROR,
          }),
          mono('sshUser', 'SSH user', 'deploy.vm.<region>.user', 4, {
            hint: 'The account the deployment signs in with',
            error: SHELL_SAFE_ERROR,
          }),
          mono('deployScript', 'Deploy script', 'deploy.vm.<region>.deployScript', 8, {
            hint: 'Installs the new version on the server',
            error: SHELL_SAFE_ERROR,
          }),
          mono('versionFile', 'Version file', 'deploy.vm.<region>.versionFile', 12, {
            error: SHELL_SAFE_ERROR,
          }),
        ],
      },
    ],
  },
  {
    id: 'limits',
    label: 'Security limits',
    note:
      'How many findings of each severity a security scan may report before it fails the ' +
      'pipeline. 0 allows none. A higher limit lets more security problems through, so agree ' +
      'it with the DevSecOps team first.',
  },
  {
    id: 'scans',
    label: 'Scans and coverage',
    note:
      'How much of the code the unit tests must cover, and how long the pipelines wait for each ' +
      'scan before they give up. The waiting times rarely need to change.',
    blocks: [
      {
        heading: 'Coverage',
        note: 'The share of the code lines the unit tests must run.',
        fields: [
          count('coverageMinLine', 'Minimum line coverage (%)', 'coverage.minLine', 4, {
            min: 1,
            max: 100,
            hint: '1 to 100',
          }),
        ],
      },
      {
        heading: 'Static scan (SAST)',
        note: 'Preparing the source code and waiting for the AppScan scan of it.',
        fields: [
          minutes(
            'sastPrepareTimeoutMinutes',
            'Longest preparation (minutes)',
            'sast.prepareTimeoutMin',
          ),
          minutes(
            'sastPollTimeoutMinutes',
            'Longest wait for the result (minutes)',
            'sast.pollTimeoutMin',
          ),
          minutes(
            'sastPollIntervalSeconds',
            'Check for the result every (seconds)',
            'sast.pollIntervalSec',
          ),
        ],
      },
      {
        heading: 'Dynamic scan (DAST)',
        note: 'The AppScan scan of the running service in the test region, and its report.',
        fields: [
          minutes(
            'dastPollTimeoutMinutes',
            'Longest wait for the result (minutes)',
            'dast.pollTimeoutMin',
            6,
          ),
          minutes(
            'dastPollIntervalSeconds',
            'Check for the result every (seconds)',
            'dast.pollIntervalSec',
            6,
          ),
          minutes(
            'dastReportTimeoutMinutes',
            'Longest wait for the report (minutes)',
            'dast.reportTimeoutMin',
            6,
          ),
          count(
            'dastReportIntervalSeconds',
            'Check for the report every (seconds)',
            'dast.reportIntervalSec',
            6,
          ),
        ],
      },
      {
        heading: 'SonarQube quality gate',
        note: "Whether the pipelines wait for SonarQube's verdict on the code quality, and for how long.",
        fields: [
          {
            key: 'sonarWaitForQualityGate',
            label: 'Wait for the quality gate',
            code: 'tools.sonar.qualityGate.waitForQualityGate',
            span: 6,
            kind: 'check',
          },
          count(
            'sonarQualityGateTimeoutMinutes',
            'Longest wait (minutes)',
            'tools.sonar.qualityGate.timeoutMinutes',
            6,
          ),
        ],
      },
    ],
  },
  {
    id: 'releaseGate',
    label: 'Release gate',
    note:
      'The last check of a run before its build may be released: the scans whose security ' +
      'limits must hold and, if ticked, the minimum line coverage. A scan left out here no ' +
      'longer stops a release.',
  },
  {
    id: 'serviceDefaults',
    label: 'Service defaults',
    note:
      'What a new service starts with, and what a service gets for a value it leaves empty. ' +
      'Each service can set its own in the product editor.',
    fields: [
      choice('buildTool', 'Build tool', GRADLE_MAVEN_FLUTTER, 'buildTool', 3),
      choice('deployTarget', 'Runs on', VM_OPENSHIFT, 'deployTarget', 3),
      mono('sourceDir', 'Source folder', 'sourceDir', 3, {
        placeholder: '.',
        hint: 'Where the code sits in the repository; a dot means the top folder',
      }),
      count('testsMaxParallel', 'Test jobs at the same time', 'tests.maxParallel', 3, { min: 1 }),
    ],
  },
  {
    id: 'goldenFix',
    label: 'GoldenFix defaults',
    note:
      'GoldenFix opens pull requests that upgrade open source libraries with known ' +
      'vulnerabilities to safe versions. A service follows these rules unless it sets its own in ' +
      'its GoldenFix section. To choose several ecosystems, hold Ctrl (Cmd on a Mac) and click.',
  },
];

export const LIMIT_FIELDS: Field[] = [
  count('maxCritical', 'Critical', '', 0, { min: 0 }),
  count('maxHigh', 'High', '', 0, { min: 0 }),
  count('maxMedium', 'Medium', '', 0, { min: 0 }),
];

export const RELEASE_GATE_FIELDS: Field[] = [
  check(
    'requireCoverage',
    'Also require the minimum line coverage',
    'releaseGate.requireCoverage',
    6,
  ),
  mono('stateFile', 'Result file', 'releaseGate.stateFile', 6, {
    hint: 'Kept with each build as proof of the check; the library reads only this name',
    readonly: true,
  }),
];

export const GOLDEN_FIX_ENABLED: Field[] = [
  check('enabled', 'GoldenFix runs by default', 'goldenFix.enabled'),
];
