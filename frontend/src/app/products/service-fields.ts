import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { AbstractControl, ReactiveFormsModule } from '@angular/forms';
import { SvgIconComponent } from 'angular-svg-icon';
import { FLUTTER_PLATFORMS, GlobalSettings, REGIONS, Region } from '../core/models';
import {
  Field,
  Fields,
  area,
  check,
  chips,
  choice,
  count,
  defaulted,
  formRevision,
  line,
  mono,
  tristate,
} from '@common/shared/fields';
import {
  HTTP_URL_ERROR,
  POWERSHELL_PATH_ERROR,
  SHELL_SAFE_ERROR,
  addItem,
  removeItem,
} from '@common/shared/form-controls';
import { TOGGLES } from '@common/ui/toggle-group';
import { GRADLE_MAVEN_FLUTTER } from '../shared/build-options';
import { AdvancedSettings } from './advanced-settings';
import { GoldenFixFields } from './golden-fix-fields';
import { OpenShiftTargetFields } from './openshift-target-fields';
import {
  ServiceForm,
  ServiceSectionId,
  ToolCommandForm,
  createNexusIqApplicationForm,
  firstInvalidSection,
  sectionInvalid,
  sectionTouched,
  visibleSections,
} from './product-form-model';
import { TestJobsFields } from './test-jobs-fields';
import { ToolCommandFields } from './tool-command-fields';
import { UrbanCodeFields } from './urban-code-fields';

const REGION_NAMES: Record<Region, string> = {
  RD: 'RD, the lower test region',
  QC: 'QC, the higher test region',
};

const NOTES: Record<ServiceSectionId, string> = {
  general: 'The name of the service, used in its Jenkins job names and reports, and what it does.',
  build: 'How Jenkins builds the service, and where the build leaves its result (the artifact).',
  unitTests:
    'The unit tests that run after the build, and where their results and coverage report are. Without a test command the step is skipped.',
  testJobs:
    'The Jenkins jobs that run the smoke, regression and performance tests after a deployment. The pipeline starts them and waits for their results.',
  deployment: 'Where the service runs: on virtual machines or on OpenShift.',
  urbanCode:
    'How the Full pipeline deploys the service to the lower test region (RD) with UrbanCode Deploy.',
  ssh: 'The virtual machines the service is deployed to over SSH, per test region. A blank value takes the library default; a region left empty is skipped.',
  openShift:
    'The OpenShift projects the service is built and deployed in, per test region. A region left empty is skipped.',
  appScan:
    "The security scans of HCL AppScan: a static scan (SAST) of the source code and, when switched on, a dynamic scan (DAST) of the running application. They sign in with the product's AppScan API key.",
  sonar: 'The code quality checks of SonarQube. Without a project key the check is skipped.',
  nexusIq:
    "The scan of the service's open source dependencies for known vulnerabilities, one entry per Nexus IQ application. Without an application the scan is skipped.",
  scm: "The Bitbucket repository of the service's code. GoldenFix raises its dependency upgrade pull requests there; without one it lists its fixes in the report only.",
  goldenFix:
    'GoldenFix proposes safe versions of the vulnerable dependencies Nexus IQ finds, as pull requests.',
  metrics:
    'Where the pipelines store their results for Pipeline Monitoring and the delivery performance (DORA) figures. The tags are the names the results are stored under.',
  flutter: 'What a Flutter build needs besides the common settings.',
};

const GENERAL: Field[] = [
  line('name', 'Service name', '', 5, {
    placeholder: 'backend-api',
    hint: 'Used in job names and reports, for example gui or backend-api',
    error: "Use letters, digits, '.', '-' or '_'",
  }),
  line('description', 'Description', '', 7, { placeholder: 'REST API and certificate scanner' }),
];

const UNIT_TEST_OPTIONS: Field[] = [
  mono('rootDir', 'Root folder', 'tests.unitTests.rootDir', 3),
  mono('reportOutDir', 'Report folder', 'tests.unitTests.reportOutDir', 3),
  check(
    'allowEmptyResults',
    'Accept a run without test results',
    'tests.unitTests.allowEmptyResults',
    6,
  ),
];

const APP_SCAN: Field[] = [
  mono('applicationId', 'AppScan application ID', 'appId', 6, {
    placeholder: '109f44ac-cc06-4ca0-884e-d944904f7019',
    hint: "the service's application in AppScan on Cloud",
    error: 'Must be the application UUID from AppScan on Cloud',
  }),
  line('sastScanName', 'SAST scan name', 'sast.scanName', 6, {
    hint: 'left empty: the service name',
  }),
];

const APP_SCAN_SECRET: Field[] = [
  mono('secretCredentialsId', 'Secret text credentials ID', 'asoc.token', 6, {
    hint: "the AppScan API key secret; left empty: the product's",
  }),
];

const APP_SCAN_STATIC: Field[] = [
  area('includedDirs', 'Included folders', 'includedDirs', 6, {
    mono: true,
    placeholder: 'src/main',
    hint: 'one per line, left empty: all',
  }),
  area('excludedDirs', 'Excluded folders', 'excludedDirs', 6, {
    mono: true,
    placeholder: 'src/test',
    hint: 'one per line',
  }),
  mono('clientPath', 'AppScan client path', 'appscanPath', 6, {
    hint: 'left empty: downloaded by the pipeline',
    error: POWERSHELL_PATH_ERROR,
  }),
];

const APP_SCAN_FLAGS: Field[] = [
  check('compile', 'Compile before preparing the IRX', 'asoc.doCompile', 6),
  check('sourceCodeOnly', 'Scan source code only', 'asoc.sourceCodeOnly', 6),
  check('useConfigFile', "Use the repository's AppScan configuration", 'asoc.useAppScanConfig', 6),
  check('insecureTls', 'Accept any TLS certificate', 'asoc.insecureTls', 6),
];

const DAST_ENABLED: Field[] = [
  check('dastEnabled', 'Run DAST against the deployed application', 'dast.enabled'),
];

const DAST: Field[] = [
  line('dastTargetUrl', 'DAST target URL', 'dast.targetUrl', 6, {
    placeholder: 'https://cert-scanner.testbbh.com',
    error: HTTP_URL_ERROR,
  }),
  line('dastScanName', 'DAST scan name', 'dast.scanName', 3),
  mono('dastPresenceId', 'Presence ID', 'dast.presenceId', 3, { hint: 'for internal hosts' }),
];

const NEXUS_IQ_APPLICATION: Field[] = [
  mono('application', 'Application', 'application', 5),
  mono('stage', 'Stage', 'stage', 3, {
    placeholder: 'build',
    error: 'A Nexus IQ stage such as build, stage-release or release',
  }),
  check('failOnNetworkError', 'Fail the build on a network error', 'failOnNetworkError', 4),
  area('scanPatterns', 'Scan patterns', 'scanPatterns', 12, {
    mono: true,
    placeholder: '**/build/libs/*.jar',
    hint: 'one Ant pattern per line',
  }),
];

const SCM: Field[] = [
  line('repositoryUrl', 'Repository URL', 'scm.bitbucket.url', 8, {
    placeholder: 'https://bitbucket.bbh.com/projects/TA/repos/cert-scanner',
    hint: "the address of the service's code in Bitbucket",
    error: HTTP_URL_ERROR,
  }),
  mono('credentialsId', 'Credentials ID', 'scm.bitbucket.credentialsId', 4, {
    placeholder: 'bitbucket-http-credentials',
    hint: 'the Jenkins credential that signs in to Bitbucket',
  }),
  mono('targetBranch', 'Target branch', 'scm.bitbucket.targetBranch', 4, {
    placeholder: 'develop',
    hint: 'the branch the pull requests go to',
  }),
  mono('reviewers', 'Reviewers', 'scm.bitbucket.reviewers', 8, {
    placeholder: 'jsmith, akowalski',
    hint: 'user names, separated by commas',
  }),
];

const SCM_OPTIONS: Field[] = [
  choice(
    'authType',
    'Sign-in',
    [
      { value: 'BASIC', label: 'User name and password or token' },
      { value: 'BEARER', label: 'HTTP access token' },
    ],
    'scm.bitbucket.authType',
    4,
  ),
  choice(
    'type',
    'Bitbucket',
    [
      { value: null, label: 'Detected from the URL' },
      { value: 'SERVER', label: 'Data Center' },
      { value: 'CLOUD', label: 'Cloud' },
    ],
    'scm.bitbucket.type',
    4,
  ),
  line('cloneUrl', 'Clone URL', 'scm.bitbucket.cloneUrl', 4, {
    placeholder: 'ssh://git@bitbucket.bbh.com/ta/cert.git',
    hint: 'left empty: the repository URL',
    error: 'Must be an http, https, ssh or git@ URL',
  }),
];

const NO_SPACES = 'No spaces or slashes';

const BITBUCKET_REPOSITORY: Field[] = [
  line('apiUrl', 'Bitbucket API URL', 'scm.bitbucket.apiUrl', 6, {
    placeholder: 'https://bitbucket.bbh.com',
    hint: 'Data Center base URL or Cloud API',
    error: HTTP_URL_ERROR,
  }),
  mono('workspace', 'Workspace', 'scm.bitbucket.workspace', 6, {
    placeholder: 'bbh-technology',
    hint: 'Bitbucket Cloud',
    error: NO_SPACES,
  }),
  mono('projectKey', 'Project key', 'scm.bitbucket.projectKey', 6, {
    placeholder: 'TA',
    hint: 'Data Center',
    error: NO_SPACES,
  }),
  mono('repoSlug', 'Repository slug', 'scm.bitbucket.repoSlug', 6, {
    placeholder: 'cert-scanner',
    hint: 'Cloud and Data Center',
    error: NO_SPACES,
  }),
];

const FLUTTER_MODULES: Field[] = [
  area('modules', 'Modules', 'tools.flutter.flutterModules', 8, {
    mono: true,
    hint: 'one per line, at least one',
  }),
  area('testModules', 'Tested modules', 'tests.modules', 4, { mono: true, hint: 'at least one' }),
  area('testSubmodules', 'Tested submodules', 'tests.submodules', 4, { mono: true }),
  area('testSubplugins', 'Tested subplugins', 'tests.subplugins', 4, { mono: true }),
];

const FLUTTER_CREDENTIALS: Field[] = [
  mono('signingPasswordCredentialsId', 'Signing password', '', 4, {
    hint: 'Jenkins credentials ID, first entry',
  }),
  mono('prodLicenseCredentialsId', 'Production licence', '', 4, { hint: 'Second entry' }),
  mono('testLicenseCredentialsId', 'Test licence', '', 4, { hint: 'Third entry' }),
];

const FLUTTER_SONAR: Field[] = [
  mono('sonarSources', 'Sources', 'tools.sonar.sources', 4, { placeholder: 'lib' }),
  mono('sonarTests', 'Tests', 'tools.sonar.tests', 4, { placeholder: 'test' }),
  mono('sonarScannerVersion', 'SonarScanner version', 'tools.sonar.sonarScannerVersion', 4, {
    placeholder: '5.0.1.3006',
    error: 'Must be a version such as 5.0.1.3006',
  }),
  mono('dartAnalyzeCommand', 'Dart analyze command', 'tools.sonar.dartAnalyzeCommand', 8),
  check('sonarFlutterPlugin', 'Flutter plugin', 'tools.sonar.flutterPlugin', 4),
];

const FLUTTER_PLATFORM: Field[] = [
  choice(
    'platform',
    'Platform',
    [
      { value: null, label: 'Library default' },
      ...FLUTTER_PLATFORMS.map((platform) => ({ value: platform, label: platform.toLowerCase() })),
    ],
    'flutter.platform',
    4,
  ),
];

@Component({
  selector: 'dso-service-fields',
  imports: [
    ReactiveFormsModule,
    SvgIconComponent,
    TOGGLES,
    AdvancedSettings,
    Fields,
    GoldenFixFields,
    OpenShiftTargetFields,
    TestJobsFields,
    ToolCommandFields,
    UrbanCodeFields,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './service-fields.html',
  styleUrl: './service-fields.scss',
})
export class ServiceFields {
  readonly form = input.required<ServiceForm>();
  readonly productCode = input('');
  readonly defaults = input<GlobalSettings | null>(null);
  readonly submitted = input(false);

  private readonly selected = signal<ServiceSectionId>('general');
  private readonly changes = formRevision(this.form);

  protected readonly regions = REGIONS;
  protected readonly regionNames = REGION_NAMES;
  protected readonly general = GENERAL;
  protected readonly appScan = APP_SCAN;
  protected readonly appScanSecret = APP_SCAN_SECRET;
  protected readonly appScanStatic = APP_SCAN_STATIC;
  protected readonly appScanFlags = APP_SCAN_FLAGS;
  protected readonly dastEnabled = DAST_ENABLED;
  protected readonly dast = DAST;
  protected readonly nexusIqApplication = NEXUS_IQ_APPLICATION;
  protected readonly scm = SCM;
  protected readonly scmOptions = SCM_OPTIONS;
  protected readonly unitTestOptions = UNIT_TEST_OPTIONS;
  protected readonly bitbucketRepository = BITBUCKET_REPOSITORY;
  protected readonly flutterModules = FLUTTER_MODULES;
  protected readonly flutterCredentials = FLUTTER_CREDENTIALS;
  protected readonly flutterSonar = FLUTTER_SONAR;
  protected readonly flutterPlatform = FLUTTER_PLATFORM;

  protected readonly sections = computed(() => {
    this.changes();
    const form = this.form();
    return visibleSections(form).map((section) => ({
      ...section,
      problem: sectionInvalid(form, section) && (this.submitted() || sectionTouched(form, section)),
    }));
  });

  protected readonly advanced = computed(() => {
    const c = this.form().controls;
    const options = (command: ToolCommandForm) =>
      Object.entries(command.controls)
        .filter(([key]) => key !== 'tasks')
        .map(([, control]) => control);
    const of = (group: { controls: Record<string, AbstractControl> }, ...keys: string[]) =>
      keys.map((key) => group.controls[key]);
    const scan = c.appScan;
    const sonar = c.sonar;
    return {
      build: [c.build.controls.sourceDir, ...options(c.build.controls.command)],
      unitTests: [
        ...of(c.unitTests, 'rootDir', 'reportOutDir', 'allowEmptyResults'),
        ...options(c.unitTests.controls.command),
      ],
      appScan: of(
        scan,
        'secretCredentialsId',
        'includedDirs',
        'excludedDirs',
        'clientPath',
        'compileCommand',
      ),
      sonar: [
        ...of(
          sonar,
          'serverUrl',
          'installationName',
          'credentialsId',
          'authTokenCredentialsId',
          'badgeToken',
        ),
        ...options(sonar.controls.command),
      ],
      nexusIq: [c.nexusIq],
      scm: of(c.scm, 'cloneUrl', 'apiUrl', 'workspace', 'projectKey', 'repoSlug'),
      metrics: of(c.metrics, 'influxUrl', 'influxCredentialsId'),
    };
  });

  protected readonly current = computed<ServiceSectionId>(() => {
    const id = this.selected();
    return this.sections().some((section) => section.id === id) ? id : 'general';
  });

  protected readonly pane = computed(() => {
    const id = this.current();
    const section = this.sections().find((candidate) => candidate.id === id);
    const coverage = this.defaults()?.scans.coverageMinLine;
    const extra: Partial<Record<ServiceSectionId, string>> = {
      unitTests: coverage === undefined ? '' : ` BBH policy requires ${coverage}% line coverage.`,
      deployment: this.isVm()
        ? ' Its environments are set under UrbanCode Deploy and SSH targets.'
        : ' Its environments are set under OpenShift targets.',
    };
    return { label: section?.label ?? '', note: chips(NOTES[id] + (extra[id] ?? '')) };
  });

  protected select(id: ServiceSectionId): void {
    this.selected.set(id);
  }

  revealFirstProblem(): boolean {
    const id = firstInvalidSection(this.form());
    if (id) {
      this.selected.set(id);
    }
    return id !== null;
  }

  protected tool() {
    return this.form().controls.build.controls.tool.value;
  }

  protected isVm(): boolean {
    return this.form().controls.deployment.controls.target.value === 'VM';
  }

  protected flutter(): boolean {
    return this.tool() === 'FLUTTER';
  }

  protected buildFields(): Field[] {
    const maven = this.tool() === 'MAVEN';
    return [
      choice('tool', 'Build tool', GRADLE_MAVEN_FLUTTER, 'buildTool', 3),
      mono('javaPath', 'JDK path', 'javaPath', 9, {
        placeholder: '/usr/lib/jvm/java-17-openjdk',
        hint: this.flutter()
          ? 'JAVA_HOME of the Flutter build stages'
          : 'JAVA_HOME of the unit tests stage, unless the build tool is set up automatically',
      }),
      check('autoSetup', 'Set up the build tool automatically', 'buildToolAutoSetup', 12),
      mono('buildPath', 'Artifact path', 'build.buildPath', 6, {
        placeholder: maven ? 'target/*.jar' : 'build/libs/*.jar',
        hint:
          this.isVm() && maven
            ? 'the artifact the Nexus snapshot delivery uploads'
            : 'what the build produces',
        error: SHELL_SAFE_ERROR,
      }),
    ];
  }

  protected buildOptions(): Field[] {
    return [
      mono('sourceDir', 'Source folder', 'sourceDir', 6, {
        placeholder: '.',
        hint: 'relative to the repository root',
      }),
    ];
  }

  protected unitTestFields(): Field[] {
    return [
      mono('resultPattern', 'Test results', 'tests.unitTests.unitTestResult', 6, {
        placeholder:
          this.tool() === 'MAVEN'
            ? 'target/surefire-reports/*.xml'
            : 'build/test-results/test/*.xml',
        hint: 'JUnit XML files',
      }),
      mono('coverageReportPath', 'Coverage report', 'coverage.reportPath', 6, {
        placeholder: 'build/reports/jacoco/test/jacocoTestReport.xml',
      }),
    ];
  }

  protected deploymentFields(): Field[] {
    const openShift = this.isVm() ? '' : 'required for OpenShift';
    return [
      mono('appName', 'Application name', 'appName', 4, {
        placeholder: 'cert-scanner-api',
        hint: openShift,
        error: SHELL_SAFE_ERROR,
      }),
      mono('artifactName', 'Artifact name', 'artifactName', 4, {
        placeholder: 'cert-scanner-api.jar',
        hint: openShift,
        error: SHELL_SAFE_ERROR,
      }),
      mono('baseArtifactName', 'Built file name', 'baseArtifactName', 4, {
        placeholder: 'app-1.0.0.jar',
        hint: 'renamed to the artifact name',
        error: SHELL_SAFE_ERROR,
      }),
    ];
  }

  protected sshFields(region: Region): Field[] {
    const deployment = this.defaults()?.deployment;
    const host = region === 'RD' ? deployment?.rdHost : deployment?.qcHost;
    return [
      mono('host', 'Host', 'host', 4, { ...defaulted(host), error: SHELL_SAFE_ERROR }),
      mono('user', 'User', 'user', 3, {
        ...defaulted(deployment?.sshUser),
        error: SHELL_SAFE_ERROR,
      }),
      mono('deployDir', 'Deployment folder', 'deployDir', 5, {
        placeholder: '/opt/ta/CertScanner/gui/deployment',
        error: SHELL_SAFE_ERROR,
      }),
      mono('deployScript', 'Deployment script', 'deployScript', 6, {
        ...defaulted(deployment?.deployScript),
        error: SHELL_SAFE_ERROR,
      }),
      mono('versionFile', 'Version file', 'versionFile', 6, {
        ...defaulted(deployment?.versionFile),
        error: SHELL_SAFE_ERROR,
      }),
    ];
  }

  protected sonarFields(): Field[] {
    return [
      line('projectName', 'Project name', 'tools.sonar.projectName', 6),
      mono('projectKey', 'Project key', 'tools.sonar.projectKey', 6, {
        hint: 'the project in SonarQube; left empty, the check is skipped',
        error: "Letters, digits, '-', '_', '.' and ':' with at least one non-digit",
      }),
    ];
  }

  protected sonarOptions(): Field[] {
    const platform = this.defaults()?.platform;
    return [
      line('serverUrl', 'Server URL', 'tools.sonar.serverUrl', 8, {
        ...defaulted(platform?.sonarServerUrl),
        error: HTTP_URL_ERROR,
      }),
      line(
        'installationName',
        'Jenkins installation',
        'tools.sonar.installationName',
        4,
        defaulted(platform?.sonarInstallationName),
      ),
      mono('credentialsId', 'Credentials ID', 'tools.sonar.credentialsId', 4),
      mono('authTokenCredentialsId', 'Token credentials ID', 'tools.sonar.authToken', 4),
      mono('badgeToken', 'Badge token', 'tools.sonar.badgeToken', 4, {
        placeholder: 'sqb_1a2b3c',
        error: 'Must be a SonarQube badge token',
      }),
      check('addBadges', 'Badges in the report', 'addBadges', 4),
      check('fullBadges', 'Every badge', 'fullBadges', 4),
    ];
  }

  protected nexusIqFields(): Field[] {
    const platform = this.defaults()?.platform;
    return [
      line('serverUrl', 'Server URL', 'tools.nexusIq.serverUrl', 5, {
        ...defaulted(platform?.nexusIqServerUrl),
        error: HTTP_URL_ERROR,
      }),
      mono(
        'credentialsId',
        'Credentials ID',
        'tools.nexusIq.credentialsId',
        3,
        defaulted(platform?.nexusIqCredentialsId),
      ),
      line('scaScanName', 'SCA scan name', 'sca.scanName', 4),
    ];
  }

  protected nexusIqApplications() {
    this.changes();
    return this.form().controls.nexusIqApplications.controls;
  }

  protected addNexusIqApplication(): void {
    addItem(this.form().controls.nexusIqApplications, createNexusIqApplicationForm());
  }

  protected removeNexusIqApplication(index: number): void {
    removeItem(this.form().controls.nexusIqApplications, index);
  }

  protected goldenFixField(): Field[] {
    const enabled = this.defaults()?.goldenFix.enabled;
    return [
      choice(
        'enabled',
        'Run GoldenFix',
        tristate('Global default', 'On', 'Off'),
        'goldenFix.enabled',
        4,
        { hint: enabled === undefined ? '' : `Global default: ${enabled ? 'on' : 'off'}` },
      ),
    ];
  }

  protected metricsFields(): Field[] {
    const project = `${this.productCode() || 'CODE'}-${this.form().controls.name.value || 'service'}`;
    return [
      check('enabled', 'Write pipeline metrics to InfluxDB', 'influx.enabled'),
      line('influxProject', 'Project tag', 'influx.project', 8, {
        placeholder: project,
        hint: `left empty: ${project}; services may share a tag`,
      }),
      mono('influxEnv', 'Environment tag', 'influx.env', 4, {
        hint: 'for example test',
        error: "Letters, digits, '.', '-' and '_'",
      }),
    ];
  }

  protected metricsOptions(): Field[] {
    const platform = this.defaults()?.platform;
    return [
      line('influxUrl', 'InfluxDB write URL', 'influx.url', 8, {
        ...defaulted(platform?.influxWriteUrl),
        error: HTTP_URL_ERROR,
      }),
      mono(
        'influxCredentialsId',
        'Credentials ID',
        'influx.credentialsId',
        4,
        defaulted(platform?.influxCredentialsId),
      ),
    ];
  }

  protected flutterDelivery(): Field[] {
    return [
      mono('deliveryGroup', 'Group', 'delivery.group', 4, {
        placeholder: 'com.bbh.payhub',
        hint: this.isVm() ? 'required on virtual machines' : '',
        error: SHELL_SAFE_ERROR,
      }),
      mono('deliveryArtifact', 'Artifact', 'delivery.artifact', 4, { error: SHELL_SAFE_ERROR }),
      mono('deliveryPlugin', 'Maven plugin', 'delivery.plugin', 4, { error: SHELL_SAFE_ERROR }),
    ];
  }

  protected inheritedGoldenFix(): string {
    const intro = 'The service follows the GoldenFix defaults of the DSOEnhanced library';
    const g = this.defaults()?.goldenFix;
    if (!g) {
      return `${intro}.`;
    }
    const dependencies = g.onlyDirectDependencies
      ? 'direct dependencies only'
      : 'direct and transitive dependencies';
    const verification = g.verifyEnabled
      ? 'each fix verified by a build'
      : 'without a verification build';
    return (
      `${intro}: ${g.ecosystems.join(', ')}, threat level ${g.minThreatLevel} and above, ` +
      `${dependencies}, ${verification}.`
    );
  }
}
