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

const minutes = (key: string, label: string, code: string, help: string, span = 4): Field =>
  count(key, label, code, span, { min: 1, help });

const limit = (key: string, severity: string): Field =>
  count(key, severity, '', 0, {
    min: 0,
    help: `The most ${severity.toLowerCase()} findings the scan may report. One more turns its stage orange and blocks the release.`,
  });

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
            help: 'The base address of the Jenkins server that runs the pipelines. Without it, only Jenkins jobs given as a full address are linked.',
          }),
          mono('jenkinsLibrary', 'Shared library', 'platform.jenkinsLibrary', 5, {
            hint: 'The name every Jenkinsfile loads with `@Library`',
            help: 'Must match the name the library is configured under in the Jenkins system settings. Jenkinsfiles already in the repositories keep the name they were copied with.',
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
            help: 'The scan client and the AppScan REST API calls use this address; its host name is also passed to the client.',
          }),
          mono('appScanClientLinuxUrl', 'Scan client download for Linux', 'SA_LINUX_URL', 6, {
            help: 'The ZIP archive of the AppScan scan client (SAClientUtil) that the pipelines download on Linux agents before a scan.',
          }),
          mono('appScanClientWindowsUrl', 'Scan client download for Windows', 'SA_WIN_URL', 6, {
            help: 'The ZIP archive of the AppScan scan client (SAClientUtil) that the pipelines download on Windows agents before a scan.',
          }),
          mono('proxyHost', 'Proxy host', 'PROXY_HOST', 5, {
            hint: 'The proxy the pipelines reach AppScan through',
            help: 'The host name only, without scheme or port. The scan client and the AppScan REST API calls go through it.',
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
            help: 'The port of the proxy, from 1 to 65535.',
          },
          mono('proxyUser', 'Proxy user', 'PROXY_USER', 4, {
            help: 'The account the pipelines sign in to the proxy with. Its password is fetched at run time from the OIS host.',
          }),
          mono('oisHost', 'OIS host', 'platform.oisHost', 6, {
            help: 'The host name of the Safeguard appliance the pipelines fetch the proxy password from. Left empty, the library uses oisapi.bbh.com.',
            error: HOST_ERROR,
          }),
        ],
      },
      {
        heading: 'SonarQube and Nexus IQ',
        note: 'The servers that check the code quality (SonarQube) and the open source libraries (Nexus IQ) of every service.',
        fields: [
          mono('sonarServerUrl', 'SonarQube server URL', 'tools.sonar.serverUrl', 7, {
            help: 'The SonarQube address of the dashboard links, the issue counts and the badges. Flutter analyses are also sent to it.',
          }),
          mono(
            'sonarInstallationName',
            'SonarQube installation',
            'tools.sonar.installationName',
            5,
            {
              hint: 'Its name in the Jenkins settings',
              help: 'The name of the SonarQube server configured in Jenkins, which the analysis and the quality gate use.',
            },
          ),
          mono('nexusIqServerUrl', 'Nexus IQ server URL', 'tools.nexusIq.serverUrl', 7, {
            help: 'The Nexus IQ Server address of the report links and the GoldenFix requests. The evaluation itself runs on the server configured in Jenkins.',
          }),
          mono(
            'nexusIqCredentialsId',
            'Nexus IQ credentials ID',
            'tools.nexusIq.credentialsId',
            5,
            {
              hint: 'The Jenkins credentials the pipelines sign in with',
              help: 'The ID of a Jenkins Username with password credential for the evaluation and the GoldenFix requests to Nexus IQ Server.',
            },
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
            {
              help: 'Flutter services upload their Android and iOS snapshot builds to this Maven repository.',
              error: SHELL_SAFE_URL_ERROR,
            },
          ),
          mono(
            'nexusSnapshotRepositoryId',
            'Nexus snapshot repository ID',
            'platform.nexusSnapshotRepositoryId',
            5,
            {
              help: 'The server ID of the repository in the Maven settings of the agents, which supplies the upload credentials.',
              error: SHELL_SAFE_ERROR,
            },
          ),
          mono('influxWriteUrl', 'InfluxDB write URL', 'influx.url', 7, {
            hint: 'Where each run stores its results',
            help: 'The complete InfluxDB v2 write address with its org, bucket and precision=s parameters.',
          }),
          mono('influxCredentialsId', 'InfluxDB credentials ID', 'influx.credentialsId', 5, {
            hint: 'The Jenkins credentials that hold the InfluxDB token',
            help: 'The ID of a Jenkins Secret text credential with an InfluxDB token that may write to the bucket of the write URL.',
          }),
          mono('iosBuildAgent', 'iOS build agent', 'platform.iosBuildAgent', 6, {
            hint: 'The Jenkins agent label of the machine iOS apps are built on',
            help: 'Flutter builds run the iOS part on an agent with this label, which must be a macOS machine. The rest of the run stays on its own agent.',
          }),
        ],
      },
      {
        heading: 'Portal monitoring (InfluxDB & Grafana)',
        note: 'Where the portal reads pipeline statistics and DORA metrics from InfluxDB, and the Grafana dashboards linked on the Pipeline Monitoring pages. Leave empty to use the server environment defaults.',
        fields: [
          mono('influxUrl', 'InfluxDB read URL', 'dso.influx.url', 6, {
            placeholder: 'http://qcwsecopsmon1.testbbh.com:8086',
            hint: 'The InfluxDB v2 address the portal queries for pipeline runs and DORA metrics',
            help: 'The base address only: the portal adds /api/v2/query itself.',
          }),
          mono('influxOrg', 'InfluxDB organization', 'dso.influx.org', 3, {
            placeholder: 'DevSecOps',
            help: 'The InfluxDB organization that owns the bucket.',
          }),
          mono('influxBucket', 'InfluxDB bucket', 'dso.influx.bucket', 3, {
            placeholder: 'DORA-metrics',
            help: 'The bucket the pipelines write their results to, as in the bucket parameter of the InfluxDB write URL.',
          }),
          mono('influxToken', 'InfluxDB read token', 'dso.influx.token', 12, {
            type: 'password',
            hint: 'Stored and never shown again. Leave empty to keep the stored token; a new InfluxDB URL needs its token again.',
            help: 'An InfluxDB API token with read access to the bucket.',
          }),
          mono('grafanaName', 'Grafana instance name', 'dso.grafana.instances[0].name', 4, {
            placeholder: 'Grafana',
            help: 'The title of the dashboards of this instance on the Pipeline Monitoring pages. Left empty: Grafana.',
          }),
          mono(
            'grafanaDashboardUrl',
            'Grafana pipeline dashboard URL',
            'dso.grafana.instances[0].dashboard-url',
            4,
            {
              placeholder: 'https://grafana.bbh.com/d/adzfc54123/devsecops-pipeline-long',
              help: 'The dashboard embedded on the monitoring page of a Full or Extended pipeline. The portal adds the project and the chosen period to the address.',
            },
          ),
          mono(
            'grafanaSecurityDashboardUrl',
            'Grafana security dashboard URL',
            'dso.grafana.instances[0].security-dashboard-url',
            4,
            {
              placeholder: 'https://grafana.bbh.com/d/ad2trcm/devsecops-security',
              help: 'The dashboard embedded on the monitoring page of a Security, SAST scanning or Nexus IQ GoldenFix pipeline. Left empty, those pages show the pipeline dashboard.',
            },
          ),
          mono('grafana2Name', 'Second Grafana instance name', 'dso.grafana.instances[1].name', 4, {
            placeholder: 'Grafana prod',
            help: 'The title of the dashboards of a second Grafana instance, shown after those of the first. Left empty: Grafana.',
          }),
          mono(
            'grafana2DashboardUrl',
            'Second Grafana pipeline dashboard URL',
            'dso.grafana.instances[1].dashboard-url',
            4,
            {
              help: 'Embedded after the pipeline dashboard of the first instance, with the same project and period.',
            },
          ),
          mono(
            'grafana2SecurityDashboardUrl',
            'Second Grafana security dashboard URL',
            'dso.grafana.instances[1].security-dashboard-url',
            4,
            {
              help: 'The security dashboard of the second Grafana instance. Left empty, its pipeline dashboard is shown.',
            },
          ),
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
          mono('urbanCodeSiteName', 'Site name', 'deploy.vm.dod.siteName', 6, {
            help: 'The UrbanCode Deploy site configured in Jenkins, for a service that deploys with UrbanCode and sets no site of its own.',
          }),
          mono('urbanCodeDeployProcess', 'Deploy process', 'deploy.vm.dod.deployProcess', 6, {
            help: 'The application process UrbanCode Deploy runs, for a service that sets no process of its own.',
          }),
        ],
      },
      {
        heading: 'Test servers',
        note: 'The servers of the two test regions and how the deployment script runs on them over SSH.',
        fields: [
          mono('rdHost', 'RD test server', 'deploy.vm.rd.host', 6, {
            hint: 'The lower test region',
            help: 'The host name of the virtual machine a service is deployed to in RD when it sets no host of its own.',
            error: HOST_ERROR,
          }),
          mono('qcHost', 'QC test server', 'deploy.vm.qc.host', 6, {
            hint: 'The higher test region',
            help: 'The host name of the virtual machine a service is deployed to in QC when it sets no host of its own.',
            error: HOST_ERROR,
          }),
          mono('sshUser', 'SSH user', 'deploy.vm.<region>.user', 4, {
            hint: 'The account the deployment signs in with',
            help: 'The account must accept the SSH key of the Jenkins agents.',
            error: SHELL_SAFE_ERROR,
          }),
          mono('deployScript', 'Deploy script', 'deploy.vm.<region>.deployScript', 8, {
            hint: 'Installs the new version on the server',
            help: 'The path of the script in the repository. It is copied to the deployment folder on the server and run there.',
            error: SHELL_SAFE_ERROR,
          }),
          mono('versionFile', 'Version file', 'deploy.vm.<region>.versionFile', 12, {
            help: 'The path of a file in the repository that is copied to the deployment folder together with the deploy script.',
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
      'How many findings of each severity a security scan may report. Above a limit its stage ' +
      'turns orange and the Nexus release and the QC deployment are blocked; the run itself ' +
      'continues. 0 allows none. A higher limit lets more security problems through, so agree ' +
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
            help: 'Below it, the Unit tests stage of the service turns orange, which blocks the Nexus release and the QC deployment.',
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
            'Not applied yet: the library allows 120 minutes for the preparation whatever this value is.',
          ),
          minutes(
            'sastPollTimeoutMinutes',
            'Longest wait for the result (minutes)',
            'sast.pollTimeoutMin',
            'How long the pipeline waits for AppScan on Cloud to finish the static scan before the SAST stage fails.',
          ),
          minutes(
            'sastPollIntervalSeconds',
            'Check for the result every (seconds)',
            'sast.pollIntervalSec',
            'How often the pipeline asks AppScan on Cloud whether the static scan has finished.',
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
            'How long the pipeline waits for AppScan on Cloud to finish the dynamic scan before the DAST stage fails.',
            6,
          ),
          minutes(
            'dastPollIntervalSeconds',
            'Check for the result every (seconds)',
            'dast.pollIntervalSec',
            'How often the pipeline asks AppScan on Cloud whether the dynamic scan has finished.',
            6,
          ),
          minutes(
            'dastReportTimeoutMinutes',
            'Longest wait for the report (minutes)',
            'dast.reportTimeoutMin',
            'How long the pipeline waits for AppScan on Cloud to render each report. Without the HTML report the stage fails; a missing PDF report is only noted.',
            6,
          ),
          count(
            'dastReportIntervalSeconds',
            'Check for the report every (seconds)',
            'dast.reportIntervalSec',
            6,
            { help: 'How often the pipeline asks AppScan on Cloud whether the report is ready.' },
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
            help: 'The SCA (SonarQube) stage turns orange when the quality gate is not passed. Unticked, only the vulnerability limits apply.',
          },
          count(
            'sonarQualityGateTimeoutMinutes',
            'Longest wait (minutes)',
            'tools.sonar.qualityGate.timeoutMinutes',
            6,
            {
              help: 'A quality gate result that does not arrive in time counts as not passed and turns the stage orange.',
            },
          ),
        ],
      },
    ],
  },
  {
    id: 'releaseGate',
    label: 'Release gate',
    note:
      'The last check of a run before its build is released to Nexus and deployed to QC. It ' +
      'blocks both when an earlier stage is not green, when a selected scan is above its ' +
      'security limits and, if ticked, when the line coverage is below the minimum.',
  },
  {
    id: 'serviceDefaults',
    label: 'Service defaults',
    note:
      'What a new service starts with in the product editor and in self-service. A saved ' +
      'service keeps its own values; only the number of test jobs at the same time also ' +
      'applies later, to every service that leaves it empty.',
    fields: [
      choice('buildTool', 'Build tool', GRADLE_MAVEN_FLUTTER, 'buildTool', 3, {
        help: 'The build tool a new service starts with. It decides which commands, test reports and artifacts the pipeline expects.',
      }),
      choice('deployTarget', 'Runs on', VM_OPENSHIFT, 'deployTarget', 3, {
        help: 'Whether a new service starts as one deployed to virtual machines or to OpenShift.',
      }),
      mono('sourceDir', 'Source folder', 'sourceDir', 3, {
        placeholder: '.',
        hint: 'Where the code sits in the repository; a dot means the top folder',
        help: 'The source folder a new service starts with. The AppScan static scan prepares this folder.',
      }),
      count('testsMaxParallel', 'Test jobs at the same time', 'tests.maxParallel', 3, {
        min: 1,
        help: 'The most jobs of one test stage that run at the same time, for every service that sets no limit of its own.',
      }),
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
  limit('maxCritical', 'Critical'),
  limit('maxHigh', 'High'),
  limit('maxMedium', 'Medium'),
];

export const RELEASE_GATE_FIELDS: Field[] = [
  check(
    'requireCoverage',
    'Also require the minimum line coverage',
    'releaseGate.requireCoverage',
    6,
    {
      help: 'Lists a line coverage below the minimum as a gate violation. The Unit tests stage turns orange in that case as well, which also blocks the release.',
    },
  ),
  mono('stateFile', 'Result file', 'releaseGate.stateFile', 6, {
    hint: 'Kept with each build as proof of the check; the library reads only this name',
    help: 'The JSON file with the verdict and its violations that each run writes. A downstream pipeline copies it and inherits the verdict.',
    readonly: true,
  }),
];

export const GOLDEN_FIX_ENABLED: Field[] = [
  check('enabled', 'GoldenFix runs by default', 'goldenFix.enabled', 12, {
    help: 'When the Nexus IQ scan of a service is above its limits, GoldenFix opens a pull request that upgrades the affected libraries. A service can turn it on or off in its own GoldenFix section.',
  }),
];
