import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { AbstractControl, ReactiveFormsModule } from '@angular/forms';
import { SvgIconComponent } from 'angular-svg-icon';
import { FLUTTER_PLATFORMS, GlobalSettings, PipelineType, REGIONS, Region } from '../core/models';
import {
  Field,
  Fields,
  area,
  check,
  chips,
  choice,
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
import { DsoHelp } from '@common/ui/help';
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
  visibleSectionsForPipeline,
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
    'How the Full pipeline deploys the service to the lower test region (RD) with UrbanCode Deploy. A Full pipeline of a service on virtual machines needs at least one application for the UrbanCode Deploy environment DV.',
  ssh: 'The virtual machines the service is deployed to over SSH, per test region. A blank value takes the library default.',
  openShift:
    'The OpenShift projects the service is built and deployed in, per test region. The QC region is optional; once one of its fields is set, the fields its deployment needs are required.',
  appScan:
    "The security scans of HCL AppScan: a static scan (SAST) of the source code and, when switched on, a dynamic scan (DAST) of the running application. Both sign in with the service's AppScan API key.",
  sonar:
    'The code quality checks of SonarQube. Without a project key the analysis still runs, but the report has no dashboard link, issue counts or badges.',
  nexusIq:
    "The scan of the service's open source dependencies for known vulnerabilities in its Nexus IQ application. Without an application the stage passes with no findings; a Nexus IQ pipeline requires one.",
  scm: "The Bitbucket repository of the service's code. GoldenFix raises its dependency upgrade pull requests there; without one it lists its fixes in the report only.",
  goldenFix:
    'GoldenFix proposes safe versions of the vulnerable dependencies Nexus IQ finds, as pull requests.',
  metrics:
    'Where the pipelines store their results for Pipeline Monitoring and the delivery performance (DORA) figures. The tags are the names the results are stored under.',
  flutter: 'What a Flutter build needs besides the common settings.',
};

const SECRET_TEXT = 'The ID of a Jenkins Secret text credential';

const GENERAL: Field[] = [
  line('name', 'Service name', '', 5, {
    placeholder: 'backend-api',
    hint: 'Used in job names and reports, for example gui or backend-api',
    help: 'Must be unique within the product, start with a letter or digit and have at most 100 characters.',
    error: "Use letters, digits, '.', '-' or '_'",
  }),
  line('description', 'Description', '', 7, {
    placeholder: 'REST API and certificate scanner',
    help: 'A short summary of what the service does. It is shown in the portal and not written to the pipeline configuration.',
  }),
];

const UNIT_TEST_OPTIONS: Field[] = [
  mono('rootDir', 'Root folder', 'tests.unitTests.rootDir', 3, {
    help: 'Maven builds: the folder searched for the target/surefire-reports folders of all modules.',
  }),
  mono('reportOutDir', 'Report folder', 'tests.unitTests.reportOutDir', 3, {
    help: 'Maven builds: the folder the Surefire reports of all modules are copied to before Jenkins reads them.',
  }),
  check(
    'allowEmptyResults',
    'Accept a run without test results',
    'tests.unitTests.allowEmptyResults',
    6,
    { help: 'Maven builds: the stage passes when no Surefire report is found.' },
  ),
];

const APP_SCAN: Field[] = [
  mono('applicationId', 'AppScan application ID', 'appId', 4, {
    placeholder: '109f44ac-cc06-4ca0-884e-d944904f7019',
    hint: "the service's application in AppScan on Cloud",
    help: 'The static and dynamic scans of the service are uploaded to this application, where AppScan on Cloud keeps their results.',
    error: 'Must be the application UUID from AppScan on Cloud',
  }),
  mono('keyId', 'AppScan API key ID', 'asoc.keyId', 4, {
    placeholder: 'bbh_1a2b3c4d',
    help: 'The ID part of the AppScan on Cloud API key the scans sign in with. Every pipeline needs it, the Nexus IQ pipeline included.',
  }),
  mono('secretCredentialsId', 'API key secret credentials ID', 'asoc.token', 4, {
    hint: 'left empty: the key ID',
    help: `${SECRET_TEXT} that holds the secret of the API key.`,
  }),
  area('includedDirs', 'Included folders', 'includedDirs', 6, {
    mono: true,
    placeholder: 'src/main',
    hint: 'one per line, left empty: all',
    help: 'The folders the static scan includes. On Linux agents a folder name matches at any depth below the source folder.',
  }),
  area('excludedDirs', 'Excluded folders', 'excludedDirs', 6, {
    mono: true,
    placeholder: 'src/test',
    hint: 'one per line',
    help: 'The folders the static scan leaves out. They apply on Linux agents, and only while no folder is included.',
  }),
];

const APP_SCAN_OPTIONS: Field[] = [
  mono('scanName', 'SAST scan name', 'sast.scanName', 6, {
    hint: 'left empty: the project name followed by -sast',
    help: "The name of the static scan in AppScan on Cloud, in lower case and with '.' replaced by '-'. The file names and the name of the dynamic scan do not change.",
    error: "Letters, digits, '.', '_' and '-' only",
  }),
  mono('clientPath', 'AppScan client path', 'appscanPath', 6, {
    hint: 'needed on Windows agents',
    help: 'The path of appscan.bat relative to the workspace, starting with a slash; Windows agents run the AppScan client from there. Linux agents use the client the pipeline downloads.',
    error: POWERSHELL_PATH_ERROR,
  }),
  check('compile', 'Compile before preparing the IRX', 'asoc.doCompile', 6, {
    help: 'Linux agents only: runs the compile command first, so the scan includes the compiled classes.',
  }),
  check('sourceCodeOnly', 'Scan source code only', 'asoc.sourceCodeOnly', 6, {
    help: 'Linux agents only: prepares the scan from the source code without compiled classes.',
  }),
  check('useConfigFile', "Use the repository's AppScan configuration", 'asoc.useAppScanConfig', 6, {
    help: 'Linux agents only: the appscan-config.xml file of the repository decides what is scanned, instead of the included and excluded folders.',
  }),
  check('insecureTls', 'Accept any TLS certificate', 'asoc.insecureTls', 6, {
    help: 'Skips the certificate check of the AppScan REST API calls the dynamic scan makes. Use it only for a known certificate problem.',
  }),
];

const DAST_ENABLED: Field[] = [
  check('dastEnabled', 'Run DAST against the deployed application', 'dast.enabled', 12, {
    help: 'After the deployment and test stages, the pipeline runs a dynamic scan of the running application at the target URL.',
  }),
];

const DAST: Field[] = [
  line('dastTargetUrl', 'DAST target URL', 'dast.targetUrl', 6, {
    placeholder: 'https://cert-scanner.testbbh.com',
    help: 'The address of the deployed application that the dynamic scan explores.',
    error: HTTP_URL_ERROR,
  }),
  mono('dastPresenceId', 'Presence ID', 'dast.presenceId', 6, {
    hint: 'for internal hosts',
    help: 'The ID of the AppScan Presence that reaches hosts inside the BBH network.',
  }),
];

const NEXUS_IQ_APPLICATION: Field[] = [
  mono('application', 'Application', 'application', 5, {
    help: 'The public ID of the application in Nexus IQ Server that receives the results.',
  }),
  mono('stage', 'Stage', 'stage', 3, {
    placeholder: 'build',
    help: 'The Nexus IQ stage the evaluation is recorded under. It decides which policy actions apply.',
    error: 'A Nexus IQ stage such as build, stage-release or release',
  }),
  check('failOnNetworkError', 'Fail the build on a network error', 'failOnNetworkError', 4, {
    help: 'Treats an unreachable Nexus IQ Server as a failed scan, which turns the stage orange and blocks the release.',
  }),
  area('scanPatterns', 'Scan patterns', 'scanPatterns', 12, {
    mono: true,
    placeholder: '**/build/libs/*.jar',
    hint: 'one Ant pattern per line',
    help: 'The files Nexus IQ scans, relative to the workspace, usually the built archives.',
  }),
];

const SCM: Field[] = [
  line('repositoryUrl', 'Repository URL', 'scm.bitbucket.url', 8, {
    placeholder: 'https://bitbucket.bbh.com/projects/TA/repos/cert-scanner',
    hint: "the address of the service's code in Bitbucket",
    help: 'The browser address of the repository. GoldenFix pushes its upgrade branch to this repository and raises the pull request there.',
    error: HTTP_URL_ERROR,
  }),
  mono('credentialsId', 'Credentials ID', 'scm.bitbucket.credentialsId', 4, {
    placeholder: 'bitbucket-http-credentials',
    hint: 'the Jenkins credential that signs in to Bitbucket',
    help: 'A Username with password credential, or a Secret text credential when the sign-in is an HTTP access token.',
  }),
  mono('targetBranch', 'Target branch', 'scm.bitbucket.targetBranch', 4, {
    placeholder: 'develop',
    hint: 'the branch the pull requests go to',
    help: 'Left empty, GoldenFix uses the branch the pipeline runs on.',
  }),
  mono('reviewers', 'Reviewers', 'scm.bitbucket.reviewers', 8, {
    placeholder: 'jsmith, akowalski',
    hint: 'user names, separated by commas',
    help: 'The Bitbucket users added as reviewers to each GoldenFix pull request. Bitbucket Cloud expects their account UUIDs.',
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
    { help: 'How GoldenFix signs in with the credential: as a user, or with a bearer token.' },
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
    { help: 'Whether the repository is on Bitbucket Data Center or Bitbucket Cloud.' },
  ),
  line('cloneUrl', 'Clone URL', 'scm.bitbucket.cloneUrl', 4, {
    placeholder: 'ssh://git@bitbucket.bbh.com/ta/cert.git',
    hint: 'left empty: derived from the repository URL',
    help: 'The address GoldenFix pushes its upgrade branch to. With an SSH address the push uses the credential as an SSH key.',
    error: 'Must be an http, https, ssh or git@ URL',
  }),
];

const NO_SPACES = 'No spaces or slashes';

const BITBUCKET_REPOSITORY: Field[] = [
  line('apiUrl', 'Bitbucket API URL', 'scm.bitbucket.apiUrl', 6, {
    placeholder: 'https://bitbucket.bbh.com',
    hint: 'Data Center base URL or Cloud API',
    help: 'The address of the Bitbucket REST API GoldenFix calls to raise pull requests.',
    error: HTTP_URL_ERROR,
  }),
  mono('workspace', 'Workspace', 'scm.bitbucket.workspace', 6, {
    placeholder: 'bbh-technology',
    hint: 'Bitbucket Cloud',
    help: 'The Bitbucket Cloud workspace that holds the repository.',
    error: NO_SPACES,
  }),
  mono('projectKey', 'Project key', 'scm.bitbucket.projectKey', 6, {
    placeholder: 'TA',
    hint: 'Data Center',
    help: 'The key of the Bitbucket Data Center project that holds the repository.',
    error: NO_SPACES,
  }),
  mono('repoSlug', 'Repository slug', 'scm.bitbucket.repoSlug', 6, {
    placeholder: 'cert-scanner',
    hint: 'Cloud and Data Center',
    help: 'The name of the repository as it appears in Bitbucket addresses.',
    error: NO_SPACES,
  }),
];

const FLUTTER_MODULES: Field[] = [
  area('modules', 'Modules', 'tools.flutter.flutterModules', 8, {
    mono: true,
    hint: 'one per line, at least one',
    help: 'The modules under the modules folder that the build prepares with flutter pub get and build_runner before the app is built.',
  }),
  area('testModules', 'Tested modules', 'tests.modules', 4, {
    mono: true,
    hint: 'at least one',
    help: 'Only named in the log of the unit tests stage. The tests that run are those of the submodules, the subplugins and the app itself.',
  }),
  area('testSubmodules', 'Tested submodules', 'tests.submodules', 4, {
    mono: true,
    help: 'The folders under modules whose tests run with coverage in the unit tests stage.',
  }),
  area('testSubplugins', 'Tested subplugins', 'tests.subplugins', 4, {
    mono: true,
    help: 'The folders under plugins whose tests run with coverage in the unit tests stage.',
  }),
];

const FLUTTER_CREDENTIALS: Field[] = [
  mono('signingPasswordCredentialsId', 'Signing password', '', 4, {
    hint: 'Jenkins credentials ID, first entry',
    help: `${SECRET_TEXT} whose value the build passes to the app as CERTIFICATE_PASSWORD.`,
  }),
  mono('prodLicenseCredentialsId', 'Production licence', '', 4, {
    hint: 'Second entry',
    help: `${SECRET_TEXT} with the licence key of the prod flavour. The pipeline builds the qc flavour, but the credential must exist.`,
  }),
  mono('testLicenseCredentialsId', 'Test licence', '', 4, {
    hint: 'Third entry',
    help: `${SECRET_TEXT} with the licence key the qc flavour build passes to the app as MISNAP_LICENSE.`,
  }),
];

const FLUTTER_SONAR: Field[] = [
  mono('sonarSources', 'Sources', 'tools.sonar.sources', 4, {
    placeholder: 'lib',
    help: 'The folders of the Dart code SonarQube analyses.',
  }),
  mono('sonarTests', 'Tests', 'tools.sonar.tests', 4, {
    placeholder: 'test',
    help: 'The folders of the Dart tests.',
  }),
  mono('sonarScannerVersion', 'SonarScanner version', 'tools.sonar.sonarScannerVersion', 4, {
    placeholder: '5.0.1.3006',
    help: "Selects the Jenkins tool installation named sonar-scanner-cli- followed by this version. Left empty, the sonar-scanner on the agent's PATH runs.",
    error: 'Must be a version such as 5.0.1.3006',
  }),
  mono('dartAnalyzeCommand', 'Dart analyze command', 'tools.sonar.dartAnalyzeCommand', 8, {
    help: 'Runs when the Flutter plugin is off; its findings, in machine format, are imported into SonarQube as external issues. Left empty: dart analyze --format=machine.',
  }),
  check('sonarFlutterPlugin', 'Flutter plugin', 'tools.sonar.flutterPlugin', 4, {
    help: 'Passes the Flutter test and coverage reports to the SonarQube Flutter plugin.',
  }),
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
    {
      help: 'Which artifact the build report records. The Flutter build itself always produces the Android APK and the iOS IPA.',
    },
  ),
];

@Component({
  selector: 'dso-service-fields',
  imports: [
    ReactiveFormsModule,
    SvgIconComponent,
    TOGGLES,
    AdvancedSettings,
    DsoHelp,
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
  readonly pipeline = input<PipelineType | null>(null);

  private readonly selected = signal<ServiceSectionId>('general');
  private readonly changes = formRevision(this.form);

  protected readonly regions = REGIONS;
  protected readonly regionNames = REGION_NAMES;
  protected readonly general = GENERAL;
  protected readonly appScan = APP_SCAN;
  protected readonly appScanOptions = APP_SCAN_OPTIONS;
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
    const pipeline = this.pipeline();
    return visibleSectionsForPipeline(form, pipeline).map((section) => ({
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
      appScan: of(scan, 'scanName', 'clientPath', 'compileCommand'),
      sonar: [...of(sonar, 'credentialsId', 'serverUrl'), ...options(sonar.controls.command)],
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

  select(id: ServiceSectionId): void {
    this.selected.set(id);
  }

  revealFirstProblem(): boolean {
    const id = firstInvalidSection(this.form(), this.pipeline());
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

  protected delivers(): boolean {
    return !this.form().controls.deployment.controls.nexusDeliveryDisabled.value;
  }

  protected buildFields(): Field[] {
    const maven = this.tool() === 'MAVEN';
    return [
      choice('tool', 'Build tool', GRADLE_MAVEN_FLUTTER, 'buildTool', 3, {
        help: 'The tool that builds the service. It decides which commands, test reports and artifacts the pipeline expects.',
      }),
      mono('javaPath', 'JDK path', 'javaPath', 9, {
        placeholder: '/usr/lib/jvm/java-17-openjdk',
        hint: this.flutter()
          ? 'must be set for Flutter builds'
          : 'JAVA_HOME of the unit tests stage, unless the build tool is set up automatically',
        help: this.flutter()
          ? 'The library refuses a Flutter build without it but does not set JAVA_HOME from it.'
          : 'The absolute path of a JDK installed on the Jenkins agent.',
      }),
      check('autoSetup', 'Set up the build tool automatically', 'buildToolAutoSetup', 12, {
        help: 'Reads the Java version from pom.xml or the Gradle build file and selects a JDK of that version installed on the agent, instead of the JDK path.',
      }),
      mono('buildPath', 'Artifact path', 'build.buildPath', 6, {
        placeholder: maven ? 'target/*.jar' : 'build/libs/*.jar',
        hint:
          this.isVm() && maven && this.delivers()
            ? 'the artifact the Nexus snapshot delivery uploads'
            : 'what the build produces',
        help: 'A path or wildcard pattern relative to the workspace; the first matching file is used. On OpenShift only its folder counts: the image build takes the artifact from there.',
        error: SHELL_SAFE_ERROR,
      }),
    ];
  }

  protected buildOptions(): Field[] {
    return [
      mono('sourceDir', 'Source folder', 'sourceDir', 6, {
        placeholder: '.',
        hint: 'relative to the repository root',
        help: 'The folder the AppScan static scan prepares. Change it when the code of the service is in a subfolder.',
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
        help: 'Gradle builds: the pattern of the test reports Jenkins publishes. Maven builds use the folders under Advanced settings.',
      }),
      mono('coverageReportPath', 'Coverage report', 'coverage.reportPath', 6, {
        placeholder: 'build/reports/jacoco/test/jacocoTestReport.xml',
        help: 'Gradle and Maven builds: the JaCoCo XML report the line coverage is read from. Left empty, the usual report paths of the build tool are tried, then any jacoco.xml in the workspace.',
      }),
    ];
  }

  protected deploymentFields(): Field[] {
    const openShift = this.isVm() ? '' : 'required for OpenShift';
    const target = this.isVm() ? 'vm' : 'openshift';
    return [
      check(
        'nexusDeliveryDisabled',
        'Disable Nexus Delivery',
        `deploy.${target}.rd.nexusDeliveryEnabled`,
        12,
        {
          help: this.isVm()
            ? 'The snapshot delivery stage still reports the build and the unit tests but publishes nothing to Nexus. The deployments still run.'
            : 'The snapshot delivery stage still reports the build and the unit tests but builds and pushes no image. The OpenShift deployments then use the pinned build tag, or are skipped without one.',
        },
      ),
      mono('appName', 'Application name', 'appName', 4, {
        placeholder: 'cert-scanner-api',
        hint: openShift,
        help: 'Names the BuildConfig and the deployment of the service on OpenShift.',
        error: SHELL_SAFE_ERROR,
      }),
      ...(this.delivers()
        ? [
            mono('artifactName', 'Artifact name', 'artifactName', 4, {
              placeholder: 'cert-scanner-api.jar',
              hint: openShift,
              help: 'The file, in the folder of the artifact path, that is copied into the image build context under this name.',
              error: SHELL_SAFE_ERROR,
            }),
            mono('baseArtifactName', 'Built file name', 'baseArtifactName', 4, {
              placeholder: 'app-1.0.0.jar',
              hint: 'renamed to the artifact name',
              help: 'The name of the file the build produces, when it differs from the artifact name.',
              error: SHELL_SAFE_ERROR,
            }),
          ]
        : []),
    ];
  }

  protected sshFields(region: Region): Field[] {
    const deployment = this.defaults()?.deployment;
    const host = region === 'RD' ? deployment?.rdHost : deployment?.qcHost;
    const c = this.form().controls.sshTargets.controls[region].controls;
    return [
      mono('host', 'Host', 'host', 4, {
        ...defaulted(host, c.host.value),
        help: 'The host name of the virtual machine the service is deployed to.',
        error: SHELL_SAFE_ERROR,
      }),
      mono('user', 'User', 'user', 3, {
        ...defaulted(deployment?.sshUser, c.user.value),
        help: "The account the pipeline signs in with over SSH; it must accept the agent's SSH key.",
        error: SHELL_SAFE_ERROR,
      }),
      mono('deployDir', 'Deployment folder', 'deployDir', 5, {
        placeholder: '/opt/ta/CertScanner/gui/deployment',
        help: 'The folder on the host that receives the script and the version file; it is created when missing. Left empty: /opt/ta/deployment.',
        error: SHELL_SAFE_ERROR,
      }),
      mono('deployScript', 'Deployment script', 'deployScript', 6, {
        ...defaulted(deployment?.deployScript, c.deployScript.value),
        help: 'The script in the repository that installs the service. It is copied to the deployment folder and run there.',
        error: SHELL_SAFE_ERROR,
      }),
      mono('versionFile', 'Version file', 'versionFile', 6, {
        ...defaulted(deployment?.versionFile, c.versionFile.value),
        help: 'The file in the repository that is copied into the deployment folder together with the script.',
        error: SHELL_SAFE_ERROR,
      }),
    ];
  }

  protected sonarFields(): Field[] {
    const platform = this.defaults()?.platform;
    const c = this.form().controls.sonar.controls;
    return [
      line('projectName', 'Project name', 'tools.sonar.projectName', 6, {
        hint: 'also names the AppScan scans and reports; left empty: the service name',
        help: 'Gradle and Maven builds take the SonarQube project name from the build file; Flutter builds pass this one to the scanner.',
      }),
      mono('projectKey', 'Project key', 'tools.sonar.projectKey', 6, {
        hint: 'left empty, the scan still runs without dashboard link, issue counts and badges',
        help: 'The key of the project in SonarQube. For Gradle and Maven it must match the key set in the build file.',
        error: "Letters, digits, '-', '_', '.' and ':' with at least one non-digit",
      }),
      line('installationName', 'Jenkins installation', 'tools.sonar.installationName', 6, {
        ...defaulted(platform?.sonarInstallationName, c.installationName.value),
        help: 'The name of the SonarQube server configured in Jenkins, which the analysis and the quality gate use.',
      }),
      mono('authTokenCredentialsId', 'Token credentials ID', 'tools.sonar.authToken', 6, {
        help: `${SECRET_TEXT} with a SonarQube token. The pipeline uses it to read the issue counts for the report.`,
      }),
      mono('badgeToken', 'Badge token', 'tools.sonar.badgeToken', 6, {
        placeholder: 'sqb_1a2b3c',
        help: 'A SonarQube project badge token that shows the metric badges in the report. Readers of the report can see it.',
        error: 'Must be a SonarQube badge token',
      }),
      mono(
        'badgeCredentialsId',
        'Badge token credentials ID',
        'tools.sonar.badgeCredentialsId',
        6,
        {
          hint: 'instead of the badge token',
          help: `${SECRET_TEXT} that holds the badge token, so the token is not stored in the portal.`,
        },
      ),
    ];
  }

  protected sonarOptions(): Field[] {
    const platform = this.defaults()?.platform;
    return [
      mono('credentialsId', 'Scanner credentials ID', 'tools.sonar.credentialsId', 4, {
        help: `${SECRET_TEXT} that replaces the server token of the Jenkins installation for Gradle and Maven analyses.`,
      }),
      line('serverUrl', 'Server URL', 'tools.sonar.serverUrl', 8, {
        ...defaulted(platform?.sonarServerUrl, this.form().controls.sonar.controls.serverUrl.value),
        help: 'The SonarQube address of the dashboard link, the issue counts and the badges. Flutter analyses are also sent to it.',
        error: HTTP_URL_ERROR,
      }),
      check('addBadges', 'Badges in the Jenkins build description', 'tools.sonar.addBadges', 6, {
        help: 'Adds the SonarQube badges of the project to the description of the Jenkins build. Gradle and Maven builds only.',
      }),
      check('fullBadges', 'Every badge', 'tools.sonar.fullBadges', 6, {
        help: 'Adds every metric badge to the build description instead of the quality gate badge only.',
      }),
    ];
  }

  protected nexusIqFields(): Field[] {
    const platform = this.defaults()?.platform;
    const c = this.form().controls.nexusIq.controls;
    return [
      line('serverUrl', 'Server URL', 'tools.nexusIq.serverUrl', 8, {
        ...defaulted(platform?.nexusIqServerUrl, c.serverUrl.value),
        help: 'The Nexus IQ Server address of the report links and the GoldenFix requests. The evaluation itself runs on the server configured in Jenkins.',
        error: HTTP_URL_ERROR,
      }),
      mono('credentialsId', 'Credentials ID', 'tools.nexusIq.credentialsId', 4, {
        ...defaulted(platform?.nexusIqCredentialsId, c.credentialsId.value),
        help: 'The ID of a Jenkins Username with password credential for the evaluation and the GoldenFix requests to Nexus IQ Server.',
      }),
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
        tristate('Library default', 'On', 'Off'),
        'goldenFix.enabled',
        4,
        {
          hint: enabled === undefined ? '' : `Library default: ${enabled ? 'on' : 'off'}`,
          help: 'Whether the pipeline proposes upgrades of vulnerable dependencies for this service.',
        },
      ),
    ];
  }

  protected metricsFields(): Field[] {
    const project = `${this.productCode() || 'CODE'}-${this.form().controls.name.value || 'service'}`;
    return [
      check('enabled', 'Write pipeline metrics to InfluxDB', 'influx.enabled', 12, {
        help: 'Sends the results of every run to InfluxDB, where Pipeline Monitoring and the DORA figures read them.',
      }),
      line('influxProject', 'Project tag', 'influx.project', 8, {
        placeholder: project,
        hint: `left empty: ${project}; services may share a tag`,
        help: 'The project the results are stored under. Services with the same tag are shown as one project.',
      }),
      mono('influxEnv', 'Environment tag', 'influx.env', 4, {
        hint: 'for example test',
        help: 'Separates the results of one project by environment. Left empty: dev.',
        error: "Letters, digits, '.', '-' and '_'",
      }),
    ];
  }

  protected metricsOptions(): Field[] {
    const platform = this.defaults()?.platform;
    const c = this.form().controls.metrics.controls;
    return [
      line('influxUrl', 'InfluxDB write URL', 'influx.url', 8, {
        ...defaulted(platform?.influxWriteUrl, c.influxUrl.value),
        help: 'The InfluxDB address the results are written to.',
        error: HTTP_URL_ERROR,
      }),
      mono('influxCredentialsId', 'Credentials ID', 'influx.credentialsId', 4, {
        ...defaulted(platform?.influxCredentialsId, c.influxCredentialsId.value),
        help: `${SECRET_TEXT} that holds the InfluxDB token.`,
      }),
    ];
  }

  protected flutterDelivery(): Field[] {
    return [
      mono('deliveryGroup', 'Group', 'delivery.group', 4, {
        placeholder: 'com.bbh.payhub',
        hint: this.isVm() ? 'required on virtual machines' : '',
        help: 'The Maven group ID the app is uploaded to Nexus under.',
        error: SHELL_SAFE_ERROR,
      }),
      mono('deliveryArtifact', 'Artifact', 'delivery.artifact', 4, {
        help: 'The Maven artifact ID of the app; the pipeline adds -android or -ios.',
        error: SHELL_SAFE_ERROR,
      }),
      mono('deliveryPlugin', 'Maven plugin', 'delivery.plugin', 4, {
        help: 'The Maven plugin goal that uploads the app, for example deploy:deploy-file.',
        error: SHELL_SAFE_ERROR,
      }),
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
