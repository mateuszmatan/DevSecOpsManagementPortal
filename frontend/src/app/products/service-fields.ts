import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { AbstractControl, FormArray, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { SvgIconComponent } from 'angular-svg-icon';
import {
  BuildTool,
  DATABASE_TARGETS,
  DatabaseTarget,
  DeployTarget,
  DiscoveredStack,
  FLUTTER_PLATFORMS,
  GlobalSettings,
  PipelineType,
  REGIONS,
  Region,
} from '../core/models';
import { ProductsApi } from '../core/api';
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
  optional,
  removeItem,
} from '@common/shared/form-controls';
import { DsoHelp } from '@common/ui/help';
import { TOGGLES } from '@common/ui/toggle-group';
import { BUILD_TOOLS, TOOL_HINTS } from '../shared/build-options';
import { RepositoryKey } from '../shared/database-rules';
import { AdvancedSettings } from './advanced-settings';
import { GoldenFixFields } from './golden-fix-fields';
import { MissingField } from './missing-fields';
import { OpenShiftTargetFields, openShiftFields } from './openshift-target-fields';
import {
  ServiceForm,
  ServiceSectionId,
  ToolCommandForm,
  createNexusIqApplicationForm,
  firstInvalidSection,
  sectionInvalid,
  formChangesDatabase,
  sectionTouched,
  visibleSectionsForPipeline,
} from './product-form-model';
import { TestJobsFields } from './test-jobs-fields';
import { ToolCommandFields, commandFields } from './tool-command-fields';
import { URBAN_CODE_APPLICATION, URBAN_CODE_COMPONENT, UrbanCodeFields } from './urban-code-fields';

const REGION_NAMES: Record<Region, string> = {
  RD: 'RD, the lower test region',
  QC: 'QC, the higher test region',
};

const DATABASE_NAMES: Record<DatabaseTarget, string> = {
  TEST: 'Test database, for the utPLSQL tests',
  ...REGION_NAMES,
};

const NOTES: Record<ServiceSectionId, string> = {
  general: 'The name of the service, used in its Jenkins job names and reports, and what it does.',
  build:
    'How Jenkins builds the service, with an optional Node.js build first, and where the build leaves its result (the artifact). A service in a subfolder of its repository names that folder under Advanced settings.',
  database:
    'The database changes of a Liquibase service. Once its changelog file is set, the pipelines check the changes, package them in a zip, install them in the test database for the utPLSQL tests when one is set, and deploy them to RD and QC in place of a deployment of the service. The database users and their passwords stay in Jenkins: the portal keeps only the IDs of their Jenkins credentials. A service with database changes runs on Linux or macOS agents only.',
  unitTests:
    'The unit tests that run after the build, and where their results and coverage reports are. Without a test command the step is skipped.',
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
    "The scan of the service's open source dependencies for known vulnerabilities in its Nexus IQ application. Without an application the stage passes with no findings; a Nexus IQ GoldenFix pipeline requires one.",
  scm: "The Bitbucket repository of the service's code. GoldenFix raises its dependency upgrade pull requests there; without one it lists its fixes in the report only.",
  goldenFix:
    'GoldenFix proposes safe versions of the vulnerable dependencies Nexus IQ finds, as pull requests.',
  metrics:
    'Where the pipelines store their results for Pipeline Monitoring and the delivery performance (DORA) figures. The tags are the names the results are stored under.',
  flutter: 'What a Flutter build needs besides the common settings.',
};

/** The notes of the sections that read differently for a Liquibase service with database changes. */
const DATABASE_NOTES: Partial<Record<ServiceSectionId, string>> = {
  build:
    'How Jenkins builds the service and where the build leaves its result (the artifact). A service with database changes runs no Node.js build: build a frontend in its Gradle or Maven service. A service in a subfolder of its repository names that folder under Advanced settings.',
  unitTests:
    'The unit tests of a service with database changes are its utPLSQL tests: tick Run the utPLSQL tests under the test database in Database changes. The Liquibase command here is optional and runs first. The test results, the coverage reports and Cypress are not read for such a service: its results and coverage are those of utPLSQL.',
  testJobs:
    'The Jenkins jobs that run the smoke, regression and performance tests after a deployment. For a service with database changes a test stage without jobs is not required, so it never turns the run orange.',
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
    help: 'The ID part of the AppScan on Cloud API key the scans sign in with. Every pipeline needs it, the Nexus IQ GoldenFix pipeline included.',
  }),
  mono('secretCredentialsId', 'API key secret credentials ID', 'asoc.token', 4, {
    hint: 'left empty: the key ID',
    help: `${SECRET_TEXT} that holds the secret of the API key.`,
  }),
];

const APP_SCAN_OPTIONS: Field[] = [
  mono('scanName', 'SAST scan name', 'sast.scanName', 6, {
    hint: 'left empty: the SonarQube project name followed by -sast',
    help: "The name of the static scan in AppScan on Cloud, in lower case and with '.' replaced by '-'. The file names and the name of the dynamic scan do not change.",
    error: "Letters, digits, '.', '_' and '-' only",
  }),
  mono('clientPath', 'AppScan client path', 'appscanPath', 6, {
    hint: 'needed on Windows agents',
    help: 'The path of appscan.bat relative to the workspace, starting with a slash; Windows agents run the AppScan client from there. Linux agents use the client the pipeline downloads.',
    error: POWERSHELL_PATH_ERROR,
  }),
  area('includedDirs', 'Included folders', 'includedDirs', 6, {
    mono: true,
    placeholder: 'src',
    hint: 'one per line, left empty: all',
    help: 'The folders the static scan includes. On Linux agents a folder name matches at any depth below the source folder.',
  }),
  area('excludedDirs', 'Excluded folders', 'excludedDirs', 6, {
    mono: true,
    placeholder: 'src/test',
    hint: 'one per line',
    help: 'The folders the static scan leaves out. They apply on Linux agents, and only while no folder is included.',
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
  check('failOnNetworkError', 'Treat a network error as a failed scan', 'failOnNetworkError', 4, {
    help: 'Treats an unreachable Nexus IQ Server as a failed scan, which turns the stage orange and blocks the release.',
  }),
  area('scanPatterns', 'Scan patterns', 'scanPatterns', 12, {
    mono: true,
    placeholder: '**/build/libs/*.jar',
    hint: 'one Ant pattern per line',
    help: 'The files Nexus IQ scans, relative to the workspace, usually the built archives. A Node.js build that scans its npm dependencies adds its package-lock.json.',
  }),
];

const NODE_BUILD =
  'Runs your npm commands in an extra stage 0, before the Gradle or Maven build, for example to build an Angular frontend that the Gradle or Maven build then packages into the jar. Write its output outside the build and target folders. Leave it off when the Gradle or Maven build already runs npm.';

const NODE_BUILD_IN: Partial<Record<PipelineType, string>> = {
  SAST: 'In SAST scanning it runs only while Compile before preparing the IRX is on, and not on Windows agents.',
  EXTENDED: 'In the Extended pipeline it runs before the release delivery rebuild.',
};

const NPM_SCAN = check(
  'nexusIqScan',
  'Scan the npm dependencies with Nexus IQ',
  'build.node.nexusIqScan',
  6,
  {
    help: 'Adds package-lock.json of this folder to the Nexus IQ scan, so Nexus IQ reports the npm dependencies and GoldenFix can raise them. New findings can turn the Nexus IQ stage orange.',
  },
);

const VERSION_CATALOGS: Field[] = [
  area('versionCatalogs', 'Shared version catalogs', 'goldenFix.versionCatalogs', 8, {
    mono: true,
    placeholder: 'platform/bom.xml',
    hint: 'optional; one path per line, relative to the repository root',
    help: "Files that hold the dependency versions several services of this repository share. GoldenFix raises a version there once for every service. It verifies a Gradle file in the builds that use it, and a Maven pom in the aggregator pom that builds it with the poms that use it; without such an aggregator, the poms that use it are not built. Found without listing: *.versions.toml files, the catalogs settings.gradle imports with from(files(...)), the files 'apply from' names, and the parent and imported BOM pom.xml files of the repository. List a file here only when GoldenFix misses it, for example a parent or BOM pom with another file name, such as platform/bom.xml.",
  }),
];

const VERSION_CATALOG_REPO: Field[] = [
  line('repositoryUrl', 'Repository URL', 'goldenFix.versionCatalogRepo.url', 8, {
    placeholder: 'https://bitbucket.bbh.com/projects/TA/repos/shared-catalogs',
    hint: 'optional external repository with version catalogs',
    help: 'An external Git or Bitbucket repository holding the version catalog files or Gradle/Maven BOMs. GoldenFix checks it out and applies dependency upgrades directly there.',
    error: HTTP_URL_ERROR,
  }),
  mono('credentialsId', 'Credentials ID', 'goldenFix.versionCatalogRepo.credentialsId', 4, {
    placeholder: 'bitbucket-http-credentials',
    hint: 'left empty: uses SCM credentials',
    help: 'The Jenkins credential used to authenticate with the external version catalog repository. If not specified, the repository credentials from the SCM section are used.',
  }),
  mono('branch', 'Branch', 'goldenFix.versionCatalogRepo.branch', 6, {
    placeholder: 'main',
    hint: 'left empty: default branch',
    help: 'The branch of the external catalog repository to check out and update.',
  }),
  mono('path', 'Path', 'goldenFix.versionCatalogRepo.path', 6, {
    placeholder: 'catalogs/libs.versions.toml',
    hint: 'path within the external repository',
    help: 'Relative path to the version catalog file or directory within the external repository.',
    error: "A path relative to the repository root, without a '..' folder, with letters, digits and . _ / * + @ : = , ~ - only",
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
    placeholder: 'https://bitbucket.bbh.com/scm/ta/cert.git',
    hint: 'left empty: derived from the repository URL',
    help: 'The address GoldenFix pushes its upgrade branch to. Use an HTTPS address: an SSH address needs an SSH key credential, which the pull request call cannot use.',
    error: 'Must be an http, https, ssh or git@ URL',
  }),
];

const NO_SPACES = 'No spaces or slashes';

const BITBUCKET_REPOSITORY: Field[] = [
  line('apiUrl', 'Bitbucket API URL', 'scm.bitbucket.apiUrl', 6, {
    placeholder: 'https://bitbucket.bbh.com',
    hint: 'Data Center base URL or Cloud API',
    help: 'Overrides the address derived from the repository URL. GoldenFix adds the REST path to a Data Center base URL and uses a Cloud API address as given.',
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

const CHANGELOG_FILE = mono('changelogFile', 'Changelog file', 'database.changelogFile', 6, {
  placeholder: 'db/changelog-master.xml',
  hint: 'the main Liquibase changelog, relative to the search folder; set it for database changes',
  help: 'The file that lists every change of the service. It ends with .xml, .yaml, .yml, .json or .sql. Once it is set, the pipelines check it, package it with the files it includes, and deploy it to the databases below; empty, the service builds with its Liquibase commands as any other Liquibase service. Its path is part of the identity of every change in the database, so it must not change after the first deployment.',
});

const DATABASE: Field[] = [
  mono('searchPath', 'Search folder', 'database.searchPath', 6, {
    placeholder: '.',
    hint: 'relative to the source folder; a dot means the source folder itself',
    help: 'Liquibase looks for the changelog and every file it includes in this folder and reads nothing outside it; the package holds this folder. Like the changelog path, it must not change after the first deployment.',
  }),
  area('parameters', 'Changelog parameters', 'database.parameters', 12, {
    mono: true,
    placeholder: 'app_role=orders_app_role',
    help: 'Values the changelog uses as ${name}, one name=value per line, such as a role or a tablespace name. A database may set its own value for a name. They are written into the package, so they must not hold a secret, and names such as password or token are refused.',
  }),
];

const TEST_RESET: Field[] = [
  check('testReset', 'Empty the test schema before each deployment', 'reset', 12, {
    help: "Drops every object of the test schema before the changes are installed, so that the changes of other branches do not remain. Tick it only for a schema that holds nothing but this service's test objects. Needs the JDBC URL of the test database.",
  }),
];

const UTPLSQL_SWITCH: Field[] = [
  check('utplsql', 'Run the utPLSQL tests', 'utplsql.enabled', 12, {
    help: 'After the changes are installed in the test database, runs the utPLSQL test packages there and reads their results and coverage. Needs the JDBC URL of the test database, with the utPLSQL framework installed in it.',
  }),
];

const UTPLSQL: Field[] = [
  mono('utplsqlPaths', 'Test suites', 'utplsql.paths', 6, {
    help: 'The suites or packages to run, separated by commas, such as orders_tests. Empty: every suite of the owner of the tests.',
  }),
  mono('utplsqlOwner', 'Owner of the tests', 'utplsql.owner', 6, {
    help: 'The schema that owns the packages and their tests. Empty: the default schema of the test database, else the schema of the database user.',
  }),
  mono('utplsqlSourcePath', 'Folder of the packages', 'utplsql.sourcePath', 6, {
    placeholder: 'db/code',
    help: 'Relative to the search folder; the folder must exist in the repository. The coverage report then names the source files.',
  }),
  mono('utplsqlTestPath', 'Folder of the test packages', 'utplsql.testPath', 6, {
    placeholder: 'db/test',
    help: 'Relative to the search folder; the folder must exist in the repository. SonarQube then treats these files as tests.',
  }),
  count('utplsqlTimeoutMinutes', 'utPLSQL timeout (minutes)', 'utplsql.timeoutMinutes', 6, {
    min: 1,
    max: 240,
    placeholder: '30',
    help: 'How long the tests may take together, from 1 to 240 minutes.',
  }),
];

const NEXUS_DELIVERY = check(
  'nexusDelivery',
  'Deliver the package to Nexus',
  'database.package.nexusDelivery',
  12,
  {
    help: 'Ticked, the Full and Security pipelines upload a zip of the changes to the snapshot repository, the Full and Extended pipelines release it after the release gate, and the Extended pipeline deploys the zip the last delivery uploaded. Unticked, nothing is uploaded, and each pipeline deploys the changes of its own checkout.',
  },
);

const GROUP_ID = mono('groupId', 'Group ID', 'database.package.groupId', 4, {
  placeholder: 'com.bbh.orders',
  help: 'The Maven group ID of the zip in Nexus. The Full, Security and Extended pipelines need it while the package is delivered.',
});

const MAVEN_HOME = mono('mvnPath', 'Maven home', 'database.package.mvnPath', 6, {
  placeholder: '/opt/maven',
  help: 'The absolute path of the Maven installation (the folder that holds bin/mvn) that uploads and fetches the zip. Empty: mvn from the PATH of the agent.',
});

const DATABASE_OPTIONS: Field[] = [
  mono('oracleVersion', 'Oracle version', 'database.oracleVersion', 3, {
    placeholder: '19.0',
    help: 'The Oracle version that the offline check of the changelog assumes. Use the version of the RD and QC databases, such as 19.0 or 23.0.',
  }),
  line('contexts', 'Contexts', 'database.contexts', 3, {
    placeholder: '!test',
    help: 'The Liquibase context filter of RD and QC. The default !test leaves out the changesets marked context:test, which only the test database receives. QC must never receive them.',
  }),
  line('labels', 'Labels', 'database.labels', 3, {
    help: 'A Liquibase label filter for every deployment. Empty: every label.',
  }),
  count('lockWaitMinutes', 'Lock wait (minutes)', 'database.lockWaitMinutes', 3, {
    min: 0,
    max: 60,
    placeholder: '5',
    help: 'How long a deployment waits for another deployment to release the Liquibase lock of the database, from 0 to 60 minutes.',
  }),
  mono('defaultSchema', 'Default schema', 'database.defaultSchema', 6, {
    help: 'The schema of the objects that the changes name without a schema. Every step that connects to a database uses it: the deployment, its status and tag, and the compile check. Empty: the schema of the database user.',
  }),
  mono('liquibaseSchema', 'Liquibase schema', 'database.liquibaseSchema', 6, {
    help: 'The schema of the tables in which Liquibase records the deployed changes (DATABASECHANGELOG) and its lock. Every step that connects to a database uses it. Empty: the default schema.',
  }),
  check('requireRollback', 'Require a rollback for every change', 'database.requireRollback', 6, {
    help: 'Ticked, the Build artifact stage checks that every change can be rolled back, and a change without a working rollback turns the stage orange. Unticked, this check does not run.',
  }),
  check('tag', 'Tag each deployment', 'database.tag', 6, {
    help: 'After a deployment that applied changes, tags the RD or QC database with the artifact ID and the version. A DBA can roll the database back to a tag by hand: that undoes the changes after the tag, newest first, and stops at the first change without a rollback. Give every change a rollback, an empty one for PL/SQL code, which then keeps its new version, and require a rollback for every change to have this checked.',
  }),
  check(
    'compileCheck',
    'Check for invalid objects after a deployment',
    'database.compileCheck.enabled',
    6,
    {
      help: 'After each deployment to RD or QC, lists the invalid PL/SQL objects of the schema. An object that the deployment made invalid fails the deployment stage; objects that were invalid before it are only listed.',
    },
  ),
  check('recompile', 'Recompile invalid objects first', 'database.compileCheck.recompile', 6, {
    help: 'Before the check, recompiles the invalid objects of the schema with their own compiler settings, so that only objects that stay invalid are listed.',
  }),
  area('ignoreObjects', 'Objects the check ignores', 'database.compileCheck.ignoreObjects', 6, {
    mono: true,
    help: 'One object name per line, at most 50, such as an old view that is invalid on purpose.',
  }),
  area('ignoreFiles', 'Files the static checks ignore', 'database.checks.ignoreFiles', 6, {
    mono: true,
    help: 'One path per line, relative to the search folder, at most 50. The checks for passwords and unsafe commands still read these files.',
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
  mono('signingPasswordCredentialsId', 'Certificate password', '', 4, {
    hint: 'first entry',
    help: `${SECRET_TEXT} whose value the build passes to the app as CERTIFICATE_PASSWORD.`,
  }),
  mono('prodLicenseCredentialsId', 'Production licence', '', 4, {
    hint: 'second entry',
    help: `${SECRET_TEXT} with the licence key of the prod flavour. The pipeline builds the qc flavour, but the credential must exist.`,
  }),
  mono('testLicenseCredentialsId', 'Test licence', '', 4, {
    hint: 'third entry',
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

function invalidControls(control: AbstractControl): AbstractControl[] {
  if (!control.invalid) {
    return [];
  }
  const children =
    control instanceof FormGroup || control instanceof FormArray
      ? Object.values<AbstractControl>(control.controls)
      : [];
  return [...(control.errors ? [control] : []), ...children.flatMap(invalidControls)];
}

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
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly regions = REGIONS;
  protected readonly regionNames = REGION_NAMES;
  protected readonly databaseTargets = DATABASE_TARGETS;
  protected readonly databaseNames = DATABASE_NAMES;
  protected readonly testReset = TEST_RESET;
  protected readonly utplsqlSwitch = UTPLSQL_SWITCH;
  protected readonly utplsqlFields = UTPLSQL;
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
  protected readonly versionCatalogs = VERSION_CATALOGS;
  protected readonly versionCatalogRepo = VERSION_CATALOG_REPO;

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
      build: [
        c.build.controls.sourceDir,
        ...options(c.build.controls.command),
        ...of(
          c.nodeBuild,
          'nodePath',
          'timeoutMinutes',
          'registry',
          'npmrcCredentialsId',
          'environment',
        ),
      ],
      unitTests: [
        ...of(c.unitTests, 'rootDir', 'reportOutDir', 'allowEmptyResults'),
        ...options(c.unitTests.controls.command),
      ],
      deployment: of(c.deployment, 'baseArtifactName'),
      appScan: of(scan, 'scanName', 'clientPath', 'includedDirs', 'excludedDirs', 'compileCommand'),
      sonar: [
        ...of(sonar, 'authTokenCredentialsId', 'credentialsId', 'serverUrl'),
        ...of(sonar, 'badgeToken', 'badgeCredentialsId'),
        ...options(sonar.controls.command),
      ],
      nexusIq: [c.nexusIq],
      scm: of(c.scm, 'cloneUrl', 'apiUrl', 'workspace', 'projectKey', 'repoSlug'),
      metrics: of(c.metrics, 'influxUrl', 'influxCredentialsId'),
      database: [
        ...of(c.database, ...this.databaseOptions().map(({ key }) => key)),
        ...DATABASE_TARGETS.flatMap((target) =>
          of(
            c.databaseTargets.controls[target],
            ...this.databaseTargetOptions(target).map(({ key }) => key),
          ),
        ),
      ],
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
    const database = this.changesDatabase();
    const extra: Partial<Record<ServiceSectionId, string>> = {
      unitTests:
        coverage === undefined
          ? ''
          : ` BBH policy requires ${coverage}% line coverage, ${database ? 'of the utPLSQL tests' : 'the average of the coverage reports of the service'}.`,
      deployment: this.isVm()
        ? ' Its environments are set under UrbanCode Deploy and SSH targets.'
        : ' Its environments are set under OpenShift targets.',
    };
    const notes = database ? { ...NOTES, ...DATABASE_NOTES } : NOTES;
    return { label: section?.label ?? '', note: chips(notes[id] + (extra[id] ?? '')) };
  });

  readonly missing = computed<MissingField[]>(() => {
    this.changes();
    const form = this.form();
    const labels = this.labels();
    const missing = visibleSectionsForPipeline(form, this.pipeline()).flatMap((section) =>
      section.keys
        .flatMap((key) => invalidControls(form.controls[key]))
        .map((control) => ({
          section: section.id,
          label: labels.get(control) ?? section.label,
          control,
        })),
    );
    return missing.filter(
      (field, index) =>
        missing.findIndex(
          (other) => other.section === field.section && other.label === field.label,
        ) === index,
    );
  });

  select(id: ServiceSectionId): void {
    this.selected.set(id);
  }

  reveal(field: MissingField): void {
    field.control.markAsTouched();
    this.selected.set(field.section);
    afterNextRender(
      () => {
        const shown = [
          ...this.host.nativeElement.querySelectorAll<HTMLElement>('.pane .has-error'),
        ];
        const target =
          shown.find(
            (candidate) =>
              candidate.querySelector('dso-label')?.textContent?.trim() === field.label,
          ) ?? shown[0];
        target?.querySelector<HTMLElement>('input, select, textarea')?.focus();
      },
      { injector: this.injector },
    );
  }

  revealFirstProblem(): boolean {
    const id = firstInvalidSection(this.form(), this.pipeline());
    if (id) {
      this.selected.set(id);
    }
    return id !== null;
  }

  private labels(): Map<AbstractControl, string> {
    const form = this.form();
    const c = form.controls;
    const commands = [
      c.build.controls.command,
      c.unitTests.controls.command,
      c.sonar.controls.command,
      c.appScan.controls.compileCommand,
      c.delivery,
    ];
    const groups: (readonly [AbstractControl, readonly Field[]])[] = [
      [form, [...GENERAL, ...VERSION_CATALOGS]],
      [c.build, [...this.buildTool(), ...this.buildFields(), ...this.buildOptions()]],
      [c.nodeBuild, [...this.nodeSwitch(), ...this.nodeFields(), ...this.nodeOptions()]],
      [c.unitTests, [...this.unitTestFields(), ...UNIT_TEST_OPTIONS]],
      [c.deployment, [...this.deploymentFields(), ...this.deploymentOptions()]],
      [c.appScan, [...APP_SCAN, ...APP_SCAN_OPTIONS, ...DAST_ENABLED, ...DAST]],
      [c.sonar, [...this.sonarFields(), ...this.sonarOptions()]],
      [c.nexusIq, this.nexusIqFields()],
      [c.scm, [...SCM, ...SCM_OPTIONS, ...BITBUCKET_REPOSITORY]],
      [c.goldenFix, this.goldenFixField()],
      [c.versionCatalogRepo, VERSION_CATALOG_REPO],
      [c.metrics, [...this.metricsFields(), ...this.metricsOptions()]],
      [c.flutter, [...FLUTTER_PLATFORM, ...FLUTTER_MODULES, ...FLUTTER_CREDENTIALS]],
      [c.flutter, [...FLUTTER_SONAR, ...this.flutterDelivery()]],
      [c.database, [CHANGELOG_FILE, ...DATABASE, ...TEST_RESET, ...UTPLSQL_SWITCH, ...UTPLSQL]],
      [c.database, [...this.packageFields(), ...this.databaseOptions()]],
      ...DATABASE_TARGETS.map(
        (target) =>
          [
            c.databaseTargets.controls[target],
            [...this.databaseTargetFields(target), ...this.databaseTargetOptions(target)],
          ] as const,
      ),
      ...commands.map((command) => [command, commandFields(this.tool(), '')] as const),
      ...REGIONS.flatMap((region) => [
        [c.sshTargets.controls[region], this.sshFields(region)] as const,
        [c.openShiftTargets.controls[region], openShiftFields(region)] as const,
      ]),
      ...c.nexusIqApplications.controls.map((app) => [app, NEXUS_IQ_APPLICATION] as const),
      ...c.urbanCodeApplications.controls.flatMap((app) => [
        [app, URBAN_CODE_APPLICATION] as const,
        ...app.controls.components.controls.map((part) => [part, URBAN_CODE_COMPONENT] as const),
      ]),
    ];
    return new Map(
      groups.flatMap(([group, fields]) =>
        fields.flatMap((field) => {
          const control = group.get(field.key);
          return control ? [[control, field.label] as const] : [];
        }),
      ),
    );
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

  /** Whether Liquibase deploys the changes of the service to its databases: its changelog file is set. */
  protected changesDatabase(): boolean {
    return formChangesDatabase(this.form());
  }

  protected delivers(): boolean {
    return !this.form().controls.deployment.controls.nexusDeliveryDisabled.value;
  }

  protected buildsNode(): boolean {
    return this.form().controls.nodeBuild.controls.enabled.value;
  }

  protected buildOptionsSummary(): string {
    if (this.flutter()) {
      return 'Source folder';
    }
    return this.buildsNode()
      ? 'Source folder, build command and Node.js options'
      : 'Source folder and build command options';
  }

  protected buildTool(): Field[] {
    return [
      choice('tool', 'Build tool', BUILD_TOOLS, 'buildTool', 6, {
        hint: TOOL_HINTS[this.tool()],
        help: 'The tool that builds the service. It decides which commands, test reports and artifacts the pipeline expects.',
      }),
    ];
  }

  protected buildFields(): Field[] {
    const maven = this.tool() === 'MAVEN';
    const liquibase = this.tool() === 'LIQUIBASE';
    const database = this.changesDatabase();
    const c = this.form().controls.build.controls;
    return [
      mono('buildPath', 'Artifact path', 'build.buildPath', 6, {
        placeholder: liquibase ? 'db/changelog/*.xml' : maven ? 'target/*.jar' : 'build/libs/*.jar',
        hint:
          this.isVm() && maven && this.delivers()
            ? 'the artifact the Nexus snapshot delivery uploads'
            : liquibase
              ? 'optional for Liquibase database migrations'
              : 'what the build produces',
        help: 'A path or wildcard pattern relative to the workspace; the first matching file is used. On OpenShift only its folder counts: the image build takes the artifact from there.',
        error: SHELL_SAFE_ERROR,
      }),
      check('autoSetup', 'Set up the build tool automatically', 'buildToolAutoSetup', 6, {
        help: database
          ? 'Selects a JDK of the Java version in the Library defaults (17 unless set there) installed on the agent, instead of the JDK path.'
          : 'Gradle and Maven builds: reads the Java version from pom.xml or the Gradle build file and selects a JDK of that version installed on the agent, instead of the JDK path.',
      }),
      ...(c.autoSetup.value && !this.flutter()
        ? []
        : [
            mono('javaPath', 'JDK path', 'javaPath', 12, {
              placeholder: '/usr/lib/jvm/java-17-openjdk',
              hint: this.flutter()
                ? 'must be set for Flutter builds'
                : database
                  ? 'the JDK Liquibase and utPLSQL run with: they need Java 17 or newer'
                  : liquibase
                    ? 'optional for Liquibase when java and liquibase are on the agent'
                    : 'JAVA_HOME of the Gradle or Maven build and the later stages',
              help: this.flutter()
                ? 'The library refuses a Flutter build without it but does not set JAVA_HOME from it.'
                : 'The absolute path of a JDK installed on the Jenkins agent.',
            }),
          ]),
    ];
  }

  protected buildOptions(): Field[] {
    return [
      mono('sourceDir', 'Source folder', 'sourceDir', 6, {
        placeholder: '.',
        hint: 'relative to the repository root; for a service in a subfolder: that folder',
        help: 'The folder the AppScan static scan prepares; the Gradle, Maven and Node.js builds have their own folder fields. Change it when the code of the service is in a subfolder.',
      }),
    ];
  }

  /** The changelog file, and the rest of the database block once it is set. */
  protected databaseFields(): Field[] {
    return [CHANGELOG_FILE, ...(this.changesDatabase() ? DATABASE : [])];
  }

  protected databaseTargetFields(target: DatabaseTarget): Field[] {
    return [
      mono('url', 'JDBC URL', 'url', 8, {
        placeholder: 'jdbc:oracle:thin:@//db-host:1521/SERVICE',
        ...(target === 'TEST' && { hint: 'a database of its own, never the RD or QC database' }),
        help: 'The Oracle thin URL of the database, without a user or a password: @//host:port/SERVICE, @tcps://host:port/SERVICE?ssl_server_dn_match=true, @host:port:SID or @(DESCRIPTION=...). The Library defaults must allow its host. Leave it empty when the pipelines do not deploy to this database.',
      }),
      mono('credentialsId', 'Credentials ID', 'credentialsId', 4, {
        help: 'The ID of a Jenkins Username with password credential of the database user, in the Jenkins folder of the product. Required with the JDBC URL. The portal never sees the password.',
      }),
    ];
  }

  protected databaseTargetOptions(target: DatabaseTarget): Field[] {
    const contexts = this.form().controls.database.controls.contexts.value.trim() || '!test';
    return [
      line('contexts', 'Contexts', 'contexts', 4, {
        placeholder: target === 'TEST' ? 'test' : contexts,
        help: 'The context filter for this database. Empty: test for the test database, and the Contexts of the service for RD and QC.',
      }),
      line('labels', 'Labels', 'labels', 4, { help: 'Empty: the Labels of the service.' }),
      mono('defaultSchema', 'Default schema', 'defaultSchema', 4, {
        help: 'Empty: the value of the service.',
      }),
      mono('liquibaseSchema', 'Liquibase schema', 'liquibaseSchema', 4, {
        help: 'Empty: the value of the service.',
      }),
      area('parameters', 'Changelog parameters', 'parameters', 8, {
        mono: true,
        help: 'Values for this database only, one name=value per line. A name set here replaces the value of the service for this database.',
      }),
      count('timeoutMinutes', 'Timeout (minutes)', 'timeoutMinutes', 4, {
        min: 1,
        max: 240,
        placeholder: '30',
        help: 'How long each step that connects to this database may take, from 1 to 240 minutes.',
      }),
    ];
  }

  /** The repositories of the package, each a main field while the Library defaults have no value for it. */
  private repositoryFields(main: boolean): Field[] {
    const defaults = this.defaults()?.database;
    const c = this.form().controls.database.controls;
    const snapshot =
      'The Nexus repository that receives the zip of every delivering build. Empty: the Library default.';
    const release =
      'The Nexus repository that receives the zip the release gate allowed. Empty: the Library default.';
    const repositories: [RepositoryKey, string, string, number, string][] = [
      ['snapshotRepositoryId', 'Snapshot repository ID', 'snapshotRepository.id', 4, snapshot],
      ['snapshotRepositoryUrl', 'Snapshot repository URL', 'snapshotRepository.url', 8, snapshot],
      ['releaseRepositoryId', 'Release repository ID', 'releaseRepository.id', 4, release],
      ['releaseRepositoryUrl', 'Release repository URL', 'releaseRepository.url', 8, release],
    ];
    return repositories
      .filter(([key]) => !optional(defaults?.[key]) === main)
      .map(([key, label, code, span, help]) =>
        mono(key, label, `database.package.${code}`, span, {
          ...defaulted(defaults?.[key], c[key].value),
          help,
        }),
      );
  }

  private deliversPackage(): boolean {
    return this.form().controls.database.controls.nexusDelivery.value;
  }

  protected packageFields(): Field[] {
    const delivers = this.deliversPackage();
    const name = this.form().controls.name.value.trim();
    return [
      NEXUS_DELIVERY,
      ...(delivers ? [GROUP_ID] : []),
      mono('artifactId', 'Artifact ID', 'database.package.artifactId', 4, {
        placeholder: name,
        hint: 'Library default: the service name',
        help: 'Names the zip and the tag of each deployment (artifact ID and version), also when the package is not delivered to Nexus.',
      }),
      ...(delivers ? this.repositoryFields(true) : []),
    ];
  }

  protected databaseOptions(): Field[] {
    return [
      ...(this.deliversPackage() ? [MAVEN_HOME, ...this.repositoryFields(false)] : []),
      ...DATABASE_OPTIONS,
    ];
  }

  protected nodeSwitch(): Field[] {
    const pipeline = this.pipeline();
    const notes = pipeline ? [NODE_BUILD_IN[pipeline] ?? ''] : Object.values(NODE_BUILD_IN);
    return [
      check('enabled', 'Build the frontend with Node.js first', 'build.node.enabled', 12, {
        help: [NODE_BUILD, ...notes].join(' ').trim(),
      }),
    ];
  }

  protected nodeFields(): Field[] {
    const defaults = this.defaults()?.serviceDefaults;
    const c = this.form().controls.nodeBuild.controls;
    return [
      mono('directory', 'Folder with package.json', 'build.node.dir', 6, {
        placeholder: 'frontend',
        hint: 'left empty: the repository root',
        help: 'The folder of the frontend in the repository. The commands run in it.',
        error: "A folder relative to the repository root, without '..' or quotes",
      }),
      area('commands', 'Commands', 'build.node.commands', 6, {
        mono: true,
        placeholder: 'npm ci\nnpm run build',
        hint: 'one per line, run in order; left empty: npm ci, then npm run build',
        help: 'Each line runs as one shell command, with sh on Linux agents and bat on Windows agents. A failing command fails the stage, and the later stages do not run.',
      }),
      ...(this.pipeline() === 'SAST' ? [] : [NPM_SCAN]),
      line('installationName', 'Node.js installation', 'build.node.installationName', 6, {
        ...defaulted(
          defaults?.nodeInstallationName,
          c.installationName.value,
          'left empty: node and npm from the agent',
        ),
        ...(c.nodePath.value.trim() && { hint: 'not used: the Node.js home on the agent is set' }),
        help: 'A NodeJS installation under Manage Jenkins, Tools, which needs the NodeJS plugin. Its node and npm run the commands.',
      }),
    ];
  }

  protected nodeOptions(): Field[] {
    const defaults = this.defaults()?.serviceDefaults;
    const c = this.form().controls.nodeBuild.controls;
    return [
      mono('nodePath', 'Node.js home on the agent', 'build.node.nodePath', 8, {
        placeholder: '/opt/node-20',
        help: 'An absolute folder of Node.js on the agent, such as /opt/node-20 or C:\\Program Files\\nodejs. It wins over the Node.js installation, the Library default included.',
        error:
          'An absolute folder such as /opt/node-20 or C:\\Program Files\\nodejs, without quotes',
      }),
      count('timeoutMinutes', 'Node.js timeout (minutes)', 'build.node.timeoutMin', 4, {
        min: 1,
        max: 240,
        hint: 'left empty: 30',
        help: 'How long the commands of the Node.js build may take together before the stage fails.',
      }),
      line('registry', 'npm registry', 'build.node.registry', 6, {
        ...defaulted(defaults?.npmRegistry, c.registry.value),
        help: 'The registry npm installs from, exported as npm_config_registry. It is not used while an .npmrc credentials ID is set; otherwise it wins over the registry line of an .npmrc in the repository.',
        error: HTTP_URL_ERROR,
      }),
      mono('npmrcCredentialsId', '.npmrc credentials ID', 'build.node.npmrcCredentialsId', 6, {
        ...defaulted(defaults?.npmrcCredentialsId, c.npmrcCredentialsId.value),
        help: 'The ID of a Jenkins Secret file credential with an .npmrc, which npm reads as its user configuration, with its registry and login. A registry line of an .npmrc in the repository still wins over it.',
      }),
      area('environment', 'Node.js environment variables', 'build.node.env', 6, {
        mono: true,
        placeholder: 'NODE_OPTIONS=--max-old-space-size=4096',
        hint: 'one NAME=value per line',
        help: 'Environment variables set for the Node.js commands and for the GoldenFix verification of npm fixes.',
      }),
    ];
  }

  protected unitTestFields(): Field[] {
    return [
      mono('resultPattern', 'Test results', 'tests.unitTests.unitTestResult', 6, {
        placeholder: 'build/test-results/test/*.xml',
        hint: 'JUnit XML files',
        help: 'Gradle builds: the pattern of the test reports Jenkins publishes. Maven builds use the folders under Advanced settings.',
      }),
      area('coverageReportPath', 'Coverage reports', 'coverage.reportPath', 6, {
        mono: true,
        hint: 'usually left empty; one per line, *, ? and ** allowed',
        help: 'Usually left empty: the pipeline then finds every JaCoCo XML and LCOV report of the service by itself, in the folder of its unit tests (the Directory of the unit test command or of the build command, otherwise the Source folder), in the folder of its Node.js build, or else in the whole repository. On Windows agents it does not look for LCOV reports: it reads the usual JaCoCo report of the build tool, and when that is missing every jacoco.xml of the workspace, or total_lcov.info for Flutter, so name every report here for those agents. A JaCoCo report needs python3 on the agent. The line coverage of the service is the average of its reports. Fill it in only to choose the reports: then only the reports named here count, so name the report of the frontend too, and for Flutter the lcov.info of each package. Paths are relative to the repository root, for example **/build/reports/jacoco/test/*.xml. When none of them exists, the pipeline searches as if the field were empty.',
      }),
      check(
        'cypressEnabled',
        'Enable Cypress unit tests',
        'tests.unitTests.cypress.enabled',
        12,
        {
          help: 'Runs Cypress unit/component tests for Node.js frontend during the unit tests stage and collects coverage from coverage/lcov.info.',
        },
      ),
      mono('cypressCommand', 'Cypress command', 'tests.unitTests.cypress.command', 6, {
        placeholder: 'npx cypress run --component',
        hint: 'left empty: npx cypress run --component',
        help: 'Custom Cypress command to execute tests. Defaults to npx cypress run --component.',
      }),
      mono('cypressReportsDir', 'Cypress reports folder', 'tests.unitTests.cypress.reportsDir', 6, {
        placeholder: 'cypress/results',
        hint: 'left empty: cypress/results',
        help: 'Directory containing JUnit XML results generated by Cypress reporter.',
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
            ? 'The snapshot delivery stage still reports the build and the unit tests but publishes nothing to Nexus. The deployments and the release delivery (Nexus delivery - Safe Artifact) still run.'
            : 'The snapshot delivery stage still reports the build and the unit tests but builds and pushes no image. The OpenShift deployments then use the pinned build tags, QC falling back to the RD tag, and a region left without a tag is skipped. The release delivery (Nexus delivery - Safe Artifact) still runs.',
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
          ]
        : []),
    ];
  }

  protected deploymentOptions(): Field[] {
    return [
      mono('baseArtifactName', 'Built file name', 'baseArtifactName', 6, {
        placeholder: 'app-1.0.0.jar',
        hint: 'renamed to the artifact name',
        help: 'The name of the file the build produces, when it differs from the artifact name.',
        error: SHELL_SAFE_ERROR,
      }),
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
    ];
  }

  protected sonarOptions(): Field[] {
    const platform = this.defaults()?.platform;
    return [
      mono('authTokenCredentialsId', 'Token credentials ID', 'tools.sonar.authToken', 6, {
        help: `${SECRET_TEXT} with a SonarQube token. The pipeline reads the open vulnerability counts with it; the report shows them and the SCA security limits are checked against them.`,
      }),
      mono('credentialsId', 'Scanner credentials ID', 'tools.sonar.credentialsId', 6, {
        help: `${SECRET_TEXT} that replaces the server token of the Jenkins installation for Gradle, Maven and Liquibase analyses. A service with database changes also asks SonarQube for its languages with it.`,
      }),
      line('serverUrl', 'Server URL', 'tools.sonar.serverUrl', 12, {
        ...defaulted(platform?.sonarServerUrl, this.form().controls.sonar.controls.serverUrl.value),
        help: 'The SonarQube address of the dashboard link, the issue counts and the badges. Flutter analyses are also sent to it.',
        error: HTTP_URL_ERROR,
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
        help: 'Separates the results of one project by environment. Left empty: test.',
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
    const intro = 'The service follows the GoldenFix defaults on the Library defaults page';
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

  private readonly api = inject(ProductsApi);
  protected readonly inspectingRepo = signal(false);
  protected readonly inspectError = signal<string | null>(null);
  protected readonly inspectSuccess = signal<string | null>(null);

  protected autoDiscover(): void {
    const scm = this.form().controls.scm.controls;
    const repoUrl = scm.repositoryUrl.value?.trim();
    if (!repoUrl) {
      scm.repositoryUrl.markAsTouched();
      this.inspectError.set('Enter a Bitbucket repository URL first.');
      return;
    }
    this.inspectingRepo.set(true);
    this.inspectError.set(null);
    this.inspectSuccess.set(null);

    this.api
      .inspectRepo({
        repositoryUrl: repoUrl,
        branch: scm.targetBranch.value?.trim() || null,
        credentialsId: scm.credentialsId.value?.trim() || null,
      })
      .subscribe({
        next: (stack) => {
          this.inspectingRepo.set(false);
          this.applyDiscoveredStack(stack);
          this.inspectSuccess.set(stack.summaryMessage || 'Stack detected and form auto-filled successfully.');
        },
        error: (err) => {
          this.inspectingRepo.set(false);
          this.inspectError.set(err?.error?.message || 'Could not inspect repository.');
        },
      });
  }

  private applyDiscoveredStack(stack: DiscoveredStack): void {
    const c = this.form().controls;
    if (stack.buildTool && ['GRADLE', 'MAVEN', 'FLUTTER', 'LIQUIBASE'].includes(stack.buildTool)) {
      c.build.controls.tool.setValue(stack.buildTool as BuildTool);
    }
    if (stack.deployTarget && ['VM', 'OPENSHIFT'].includes(stack.deployTarget)) {
      c.deployment.controls.target.setValue(stack.deployTarget as DeployTarget);
    }
    if (stack.nodeBuildEnabled) {
      c.nodeBuild.controls.enabled.setValue(true);
    }
    const database = formChangesDatabase(this.form());
    if (stack.buildTasks && !database) {
      c.build.controls.command.controls.tasks.setValue(stack.buildTasks);
    }
    if (stack.unitTestTasks && !database) {
      c.unitTests.controls.command.controls.tasks.setValue(stack.unitTestTasks);
    }
    if (stack.sonarProjectKey && !c.sonar.controls.projectKey.value) {
      c.sonar.controls.projectKey.setValue(stack.sonarProjectKey);
    }
  }
}
