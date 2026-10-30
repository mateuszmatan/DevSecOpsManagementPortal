import { DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import {
  AppScanSettings,
  BitbucketAuthType,
  BitbucketType,
  BuildTool,
  DeployTarget,
  FlutterPlatform,
  FlutterSettings,
  GlobalGoldenFixPolicy,
  GlobalSettings,
  GoldenFixPolicy,
  NexusIqApplication,
  NodeBuildSettings,
  OpenShiftTarget,
  PipelineType,
  Product,
  ProductRequest,
  REGIONS,
  Region,
  ServiceDefaults,
  ServiceRequest,
  ServiceTemplateValues,
  SshTarget,
  TestJob,
  TestJobType,
  TestStage,
  ToolCommand,
  UrbanCodeApplicationSettings,
  UrbanCodeComponent,
} from '../core/models';
import { FieldProblem, PRODUCT_CODE } from '@common/core/models';
import {
  IMAGE_TAG,
  INT_MAX,
  INT_MIN,
  POWERSHELL_PATH,
  Sent,
  applyFieldProblems,
  commaItems,
  eachItem,
  filled,
  fitsColumn,
  flag,
  integer,
  joinLines,
  joinWords,
  lines,
  max,
  maxLines,
  maxWords,
  optional,
  passesValidators,
  requireWhile,
  requiredRule,
  requiredWhen,
  revalidateOnChange,
  sent,
  setEnabled,
  shellSafe,
  text,
  url,
  words,
} from '@common/shared/form-controls';
import { Observable, combineLatest, filter, map, pairwise, startWith } from 'rxjs';
import { agentLabels } from '../shared/build-options';
import { buildDefaults, imageRepository, namedDefaults } from '../shared/service-template';
import {
  applyLibraryDefaults,
  isUntouchedDefault,
  withoutUntouchedDefaults,
} from './library-defaults';

export const SERVICE_NAME = /^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/;
export const UUID =
  /^\s*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\s*$/;
export const METRICS_TAG = /^[A-Za-z0-9._-]*$/;
export const SONAR_KEY = /^([a-zA-Z0-9_.:-]*[a-zA-Z_.:-][a-zA-Z0-9_.:-]*)?$/;
export const ENV_VARIABLE = /^[A-Za-z_][A-Za-z0-9_+]*=.*$/;
export const JOB_PARAMETER = /^[A-Za-z_][A-Za-z0-9_.-]*=.*$/;
export const GIT_URL = /^(https?:\/\/\S+|ssh:\/\/\S+|git@\S+)$/;
export const BITBUCKET_NAME = /^[^\s/]*$/;
export const UCD_ENVIRONMENT = /^[A-Za-z0-9_-]{1,20}$/;
export const MODULE_FOLDER = /^[A-Za-z0-9._/-]{1,100}$/;
export const NEXUS_STAGE = /^[a-z-]*$/;
export const BADGE_TOKEN = /^[A-Za-z0-9_]*$/;
export const SCAN_NAME = /^[A-Za-z0-9._-]*$/;
export const SCANNER_VERSION = /^[0-9A-Za-z._-]*$/;
export const TIME_ZONE = /^[A-Za-z0-9_+/-]*$/;
export const REMEDIATION_TYPE = /^[a-z-]{1,100}$/;
export const GOLDEN_FIX_ECOSYSTEMS = ['maven', 'npm', 'pypi', 'pub'];
export const RELATIVE_FOLDER = /^(?![/\\])(?!.*\.\.)[^'"\p{Cc}]*$/u;
export const ABSOLUTE_FOLDER = /^((\/|[A-Za-z]:[/\\]|\\\\)[^'"\p{Cc}]*)?$/u;
export const REPOSITORY_PATH = /^(?!\/)(?!(.*\/)?\.\.(\/|$))[A-Za-z0-9._/*+@:=,~-]*$/;
export const REPOSITORY_FILE = /^(?!.*\/$).*$/;

/**
 * A path relative to the repository root as the portal keeps it, without its '.' and empty
 * folders, so './web/' is 'web' and '.' is null, the root itself. A path that starts at '/' is
 * kept as it is for validation to refuse.
 */
export function relativePath(path: string | null | undefined): string | null {
  const trimmed = optional(path);
  if (trimmed === null || trimmed.startsWith('/')) {
    return trimmed;
  }
  return optional(
    trimmed
      .split('/')
      .filter((folder) => folder && folder !== '.')
      .join('/'),
  );
}

const relativePaths = (value: string) => [
  ...new Set(lines(value).flatMap((path) => relativePath(path) ?? [])),
];

/** A folder of the repository, judged as the portal keeps it, so the form refuses what the server refuses. */
const relativeFolder: ValidatorFn = (control) =>
  RELATIVE_FOLDER.test(relativePath(control.value) ?? '') ? null : { pattern: true };

const FOLDER = /^[^,']{1,300}$/;
const COMMAND = /^\P{Cc}*$/u;
const tokenLines = (value: string) => lines(value, false);
const parameterLines = (value: string) => value.split('\n').filter((line) => line.trim());
const tokenWords = (value: string) => words(value, false);
const upTo = (length: number) => new RegExp(`^.{1,${length}}$`);
const environment = (values: string[] | null | undefined, column: number) =>
  text(
    joinLines(values),
    maxLines(30, false),
    eachItem(tokenLines, ENV_VARIABLE, 'Write each variable as NAME=value'),
    eachItem(tokenLines, upTo(500), 'At most 500 characters per variable'),
    fitsColumn(tokenLines, '\n', column),
  );

export const NO_COMMAND: ToolCommand = {
  tasks: [],
  flags: [],
  directory: null,
  mavenHome: null,
  environment: [],
  label: null,
  returnStdout: false,
};

export function createToolCommandForm(command?: Partial<ToolCommand> | null) {
  return new FormGroup({
    tasks: text(
      joinWords(command?.tasks),
      maxWords(30, false),
      eachItem(tokenWords, upTo(200), 'At most 200 characters per task'),
      fitsColumn(tokenWords, '\n', 1000),
    ),
    flags: text(
      joinLines(command?.flags),
      maxLines(40, false),
      eachItem(tokenLines, upTo(300), 'At most 300 characters per flag'),
      fitsColumn(tokenLines, '\n', 2000),
    ),
    directory: text(command?.directory, max(500)),
    mavenHome: shellSafe(command?.mavenHome, 500),
    environment: environment(command?.environment, 4000),
    label: text(command?.label, max(200)),
    returnStdout: flag(command?.returnStdout),
  });
}

export type ToolCommandForm = ReturnType<typeof createToolCommandForm>;

export function toToolCommand(form: ToolCommandForm): ToolCommand {
  const v = form.getRawValue();
  return {
    tasks: words(v.tasks, false),
    flags: lines(v.flags, false),
    directory: optional(v.directory),
    mavenHome: kept(form.controls.mavenHome),
    environment: lines(v.environment, false),
    label: optional(v.label),
    returnStdout: v.returnStdout,
  };
}

export function isJobUrl(job: unknown): boolean {
  const value = typeof job === 'string' ? job.trim() : '';
  return value.startsWith('http://') || value.startsWith('https://');
}

export const REMOTE_JENKINS_MESSAGE =
  'Name the remote Jenkins or its URL, or give the job as a full URL';

const LOCAL_JOB = {
  remoteJenkins: null,
  remoteJenkinsUrl: null,
  credentialsId: null,
  pollIntervalSec: null,
  tokenCredentialsId: null,
  abortTriggeredJob: false,
  overrideTrustAllCertificates: false,
  preventRemoteBuildQueue: false,
  trustAllCertificates: false,
  useCrumbCache: false,
  useJobInfoCache: false,
} satisfies Partial<TestJob>;

const REMOTE_ONLY = Object.keys(LOCAL_JOB) as (keyof typeof LOCAL_JOB)[];

export function createTestJobForm(job?: Partial<TestJob> | null) {
  const form = new FormGroup({
    stage: new FormControl<TestStage>(job?.stage ?? 'SMOKE', { nonNullable: true }),
    name: text(job?.name, max(200)),
    type: new FormControl<TestJobType | null>(job?.type ?? null),
    job: text(job?.job, filled, max(1000)),
    timeoutMinutes: integer(job?.timeoutMinutes, 1, INT_MAX),
    parameters: text(
      job?.parameters,
      max(2000),
      eachItem(parameterLines, JOB_PARAMETER, 'Write each parameter as NAME=value'),
    ),
    remoteJenkins: text(
      job?.remoteJenkins,
      max(200),
      requiredWhen(
        (j) =>
          j['type'] === 'REMOTE' && !isJobUrl(j['job']) && !optional(String(j['remoteJenkinsUrl'])),
        REMOTE_JENKINS_MESSAGE,
      ),
    ),
    remoteJenkinsUrl: url(job?.remoteJenkinsUrl),
    credentialsId: text(job?.credentialsId, max(200)),
    pollIntervalSec: integer(job?.pollIntervalSec, 1, INT_MAX),
    tokenCredentialsId: text(job?.tokenCredentialsId, max(200)),
    abortTriggeredJob: flag(job?.abortTriggeredJob),
    overrideTrustAllCertificates: flag(job?.overrideTrustAllCertificates),
    preventRemoteBuildQueue: flag(job?.preventRemoteBuildQueue),
    trustAllCertificates: flag(job?.trustAllCertificates),
    useCrumbCache: flag(job?.useCrumbCache),
    useJobInfoCache: flag(job?.useJobInfoCache),
  });
  const { type, remoteJenkins, remoteJenkinsUrl } = form.controls;
  [type, form.controls.job, remoteJenkinsUrl].forEach((source) =>
    revalidateOnChange(source, remoteJenkins),
  );
  const syncRemote = () =>
    REMOTE_ONLY.forEach((key) => setEnabled(form.controls[key], isRemoteJob(form)));
  type.valueChanges.subscribe(syncRemote);
  form.controls.job.valueChanges.subscribe(syncRemote);
  syncRemote();
  remoteJenkins.updateValueAndValidity();
  return form;
}

export type TestJobForm = ReturnType<typeof createTestJobForm>;

export function isRemoteJob(form: TestJobForm): boolean {
  return form.controls.type.value === 'REMOTE' || isJobUrl(form.controls.job.value);
}

export function toTestJob(form: TestJobForm): TestJob {
  const v = form.getRawValue();
  const job = { ...sent(v), parameters: v.parameters.trim() ? v.parameters : null };
  return isRemoteJob(form) ? job : { ...job, ...LOCAL_JOB };
}

export function createUrbanCodeComponentForm(component?: Partial<UrbanCodeComponent> | null) {
  return new FormGroup({
    componentName: text(component?.componentName, filled, max(200)),
    baseDir: text(component?.baseDir, filled, max(500)),
    fileIncludePatterns: text(component?.fileIncludePatterns, filled, max(500)),
    fileExcludePatterns: text(component?.fileExcludePatterns, max(500)),
    versionPrefix: text(component?.versionPrefix, max(200)),
    version: text(component?.version, max(200)),
    incrementalVersion: flag(component?.incrementalVersion, true),
    extensions: text(component?.extensions, max(200)),
    charset: text(component?.charset, max(50)),
    pushDescription: text(component?.pushDescription, max(1000)),
    versionProperties: text(component?.versionProperties, max(2000)),
    versionDescription: text(component?.versionDescription, max(1000)),
  });
}

export function createUrbanCodeApplicationForm(
  application?: Partial<UrbanCodeApplicationSettings> | null,
) {
  const components = application ? (application.components ?? []) : [null];
  const threeState = (value: boolean | null | undefined) =>
    new FormControl<boolean | null>(value ?? null);
  return new FormGroup({
    applicationName: text(application?.applicationName, filled, max(200)),
    order: integer(application?.order, INT_MIN, INT_MAX),
    environments: text(
      joinWords(application?.environments, ', '),
      maxWords(20),
      eachItem(words, UCD_ENVIRONMENT, "Use letters, digits, '-' and '_', at most 20 characters"),
    ),
    snapshotName: text(application?.snapshotName, max(200)),
    siteName: text(application?.siteName, max(200)),
    deployProcess: text(application?.deployProcess, max(200)),
    skipWait: threeState(application?.skipWait),
    deployWithSnapshot: threeState(application?.deployWithSnapshot),
    updateSnapshotComponents: threeState(application?.updateSnapshotComponents),
    includeOnlyDeployVersions: threeState(application?.includeOnlyDeployVersions),
    deployOnlyChanged: threeState(application?.deployOnlyChanged),
    deployDescription: text(application?.deployDescription, max(1000)),
    description: text(application?.description, max(1000)),
    requestProperties: text(application?.requestProperties, max(2000)),
    components: new FormArray(
      components.map(createUrbanCodeComponentForm),
      requiredRule('Add at least one component'),
    ),
  });
}

export type UrbanCodeApplicationForm = ReturnType<typeof createUrbanCodeApplicationForm>;

const DV_APPLICATION =
  'Add an UrbanCode Deploy application for DV: the Full pipeline uses it to deploy to the lower test region';

export const deploysToDv = (environments: readonly string[]) =>
  !environments.length || environments.some((name) => name.trim().toUpperCase() === 'DV');

export const dvApplication: ValidatorFn = (control) =>
  (control.value as { environments: string }[]).some(({ environments }) =>
    deploysToDv(words(environments)),
  )
    ? null
    : { rule: DV_APPLICATION };

function toUrbanCodeApplication(form: UrbanCodeApplicationForm): UrbanCodeApplicationSettings {
  const v = form.getRawValue();
  return {
    ...sent(v),
    environments: words(v.environments),
    components: v.components.map(sent),
  };
}

export function createNexusIqApplicationForm(application?: Partial<NexusIqApplication> | null) {
  return new FormGroup({
    application: text(application?.application, filled, max(200)),
    scanPatterns: text(
      joinLines(application?.scanPatterns),
      filled,
      maxLines(20),
      eachItem(lines, upTo(300), 'At most 300 characters per pattern'),
      fitsColumn(lines, '\n', 2000),
    ),
    stage: text(application?.stage ?? 'build', Validators.pattern(NEXUS_STAGE), max(50)),
    failOnNetworkError: flag(application?.failOnNetworkError),
  });
}

export function nexusIqApplicationForm(application: string, scanPattern: string) {
  return createNexusIqApplicationForm({
    application,
    scanPatterns: scanPattern ? [scanPattern] : [],
  });
}

export function createSshTargetForm(target?: Partial<SshTarget> | null) {
  return new FormGroup({
    host: shellSafe(target?.host, 255),
    user: shellSafe(target?.user, 100),
    deployDir: shellSafe(target?.deployDir, 500),
    deployScript: shellSafe(target?.deployScript, 500),
    versionFile: shellSafe(target?.versionFile, 500),
  });
}

export const OPENSHIFT_RD_REQUIRED: readonly (keyof OpenShiftTarget)[] = [
  'projectBuild',
  'buildConfigPath',
  'dockerFilePath',
  'buildContext',
  'dockerRepoPush',
  'nexusAuthFile',
];

export const OPENSHIFT_IMAGE_DELIVERY: readonly (keyof OpenShiftTarget)[] = [
  'projectBuild',
  'buildConfigPath',
  'dockerFilePath',
  'buildContext',
  'addFile',
  'dockerRepoPush',
  'certDir',
  'nexusAuthFile',
  'internalDockerUrl',
];

export const OPENSHIFT_QC_REQUIRED: readonly (keyof OpenShiftTarget)[] = [
  'projectDeployment',
  'deployConfigPath',
  'dockerRepoPull',
  'configPath',
];

export function createOpenShiftTargetForm(
  t?: Partial<OpenShiftTarget> | null,
  required: readonly (keyof OpenShiftTarget)[] = [],
) {
  const form = new FormGroup({
    projectBuild: text(t?.projectBuild, max(200)),
    buildConfigPath: shellSafe(t?.buildConfigPath, 500),
    dockerFilePath: shellSafe(t?.dockerFilePath, 500),
    buildContext: shellSafe(t?.buildContext, 500),
    addFile: shellSafe(t?.addFile, 500),
    dockerRepoPush: shellSafe(t?.dockerRepoPush, 500),
    dockerRepoPull: text(t?.dockerRepoPull, max(500)),
    certDir: shellSafe(t?.certDir, 500),
    nexusAuthFile: shellSafe(t?.nexusAuthFile, 500),
    projectDeployment: text(t?.projectDeployment, max(200)),
    deployConfigPath: text(t?.deployConfigPath, max(500)),
    configPath: text(t?.configPath, max(500)),
    skipConfigDeploy: flag(t?.skipConfigDeploy),
    healthCheckUrl: text(t?.healthCheckUrl, max(500)),
    routeHostname: text(t?.routeHostname, max(300)),
    deploymentPath: text(t?.deploymentPath, max(500)),
    deploymentRepoUrl: text(t?.deploymentRepoUrl, Validators.pattern(GIT_URL), max(1000)),
    deploymentRepoBranch: text(t?.deploymentRepoBranch, max(200)),
    deploymentRepoCredentialsId: text(t?.deploymentRepoCredentialsId, max(200)),
    buildTag: text(t?.buildTag, Validators.pattern(IMAGE_TAG), max(500)),
    internalDockerUrl: text(t?.internalDockerUrl, Validators.pattern(IMAGE_TAG), max(500)),
  });
  required.forEach((key) => {
    form.controls[key].addValidators(filled);
    form.controls[key].updateValueAndValidity();
  });
  return form;
}

export type OpenShiftTargetForm = ReturnType<typeof createOpenShiftTargetForm>;

export const inUse = (target: Record<string, unknown>) =>
  Object.values(target).some((value) =>
    typeof value === 'boolean' ? value : !!optional(String(value ?? '')),
  );

function requireOnceUsed(target: OpenShiftTargetForm): void {
  OPENSHIFT_QC_REQUIRED.forEach((key) =>
    requireWhile(
      target.controls[key],
      () =>
        inUse(target.getRawValue()) &&
        (key !== 'configPath' || !target.controls.skipConfigDeploy.value),
      target,
    ),
  );
}

export function goldenFixControls(policy?: Partial<GoldenFixPolicy> | null, complete = false) {
  const required = complete ? [filled] : [];
  const command = (value: string | null | undefined) => text(value, max(500));
  return {
    onlyDirectDependencies: new FormControl<boolean | null>(
      policy?.onlyDirectDependencies ?? null,
      required,
    ),
    minThreatLevel: integer(policy?.minThreatLevel, 0, 10, ...required),
    ecosystems: new FormControl<string[]>(policy?.ecosystems ?? [], {
      nonNullable: true,
      validators: required,
    }),
    goldenVersionTypes: text(
      joinLines(policy?.goldenVersionTypes),
      ...required,
      maxLines(10),
      eachItem(lines, REMEDIATION_TYPE, 'Use a Nexus IQ remediation type'),
      fitsColumn(lines, '\n', 1000),
    ),
    excludeDirs: text(
      joinLines(policy?.excludeDirs),
      maxLines(30),
      eachItem(lines, upTo(200), 'At most 200 characters per folder'),
      fitsColumn(lines, '\n', 2000),
    ),
    verifyEnabled: new FormControl<boolean | null>(policy?.verifyEnabled ?? null, required),
    verifyMaxAttempts: integer(policy?.verifyMaxAttempts, 1, INT_MAX, ...required),
    verifyTimeoutMinutes: integer(policy?.verifyTimeoutMinutes, 1, INT_MAX, ...required),
    verifyMavenCommand: command(policy?.verifyMavenCommand),
    verifyGradleCommand: command(policy?.verifyGradleCommand),
    verifyNpmCommand: command(policy?.verifyNpmCommand),
    verifyPipCommand: command(policy?.verifyPipCommand),
    verifyPubCommand: command(policy?.verifyPubCommand),
    commitAuthorName: text(policy?.commitAuthorName, ...required, max(200)),
    commitAuthorEmail: text(policy?.commitAuthorEmail, ...required, Validators.email, max(320)),
    timeZone: text(policy?.timeZone, Validators.pattern(TIME_ZONE), max(100)),
  };
}

export type GoldenFixControls = ReturnType<typeof goldenFixControls>;
export type GoldenFixValue = ReturnType<FormGroup<GoldenFixControls>['getRawValue']>;
export type GoldenFixOverrides = Omit<GoldenFixPolicy, 'enabled'>;

export function toGoldenFixOverrides(v: GoldenFixValue): GoldenFixOverrides {
  return {
    ...sent(v),
    ecosystems: [...new Set(v.ecosystems)],
    goldenVersionTypes: lines(v.goldenVersionTypes),
    excludeDirs: lines(v.excludeDirs),
  };
}

export const NO_GOLDEN_FIX_OVERRIDES: GoldenFixOverrides = toGoldenFixOverrides(
  new FormGroup(goldenFixControls()).getRawValue(),
);

export function inheritsGoldenFix(policy?: Partial<GoldenFixPolicy> | null): boolean {
  if (!policy) {
    return true;
  }
  const { enabled, ...overrides } = policy;
  return Object.values(overrides).every(
    (value) => value === null || value === undefined || (Array.isArray(value) && !value.length),
  );
}

export function createGlobalGoldenFixForm(policy?: Partial<GlobalGoldenFixPolicy> | null) {
  return new FormGroup({
    enabled: flag(policy?.enabled, true),
    ...goldenFixControls(policy, true),
  });
}

export type GlobalGoldenFixForm = ReturnType<typeof createGlobalGoldenFixForm>;

export function toGlobalGoldenFixPolicy(form: GlobalGoldenFixForm): GlobalGoldenFixPolicy {
  const { enabled, ...overrides } = form.getRawValue();
  return { enabled, ...toGoldenFixOverrides(overrides) };
}

export function createServiceGoldenFixForm(policy?: Partial<GoldenFixPolicy> | null) {
  const form = new FormGroup({
    inherit: flag(inheritsGoldenFix(policy)),
    enabled: new FormControl<boolean | null>(policy?.enabled ?? null),
    ...goldenFixControls(policy),
  });
  const sync = () => {
    for (const [key, control] of Object.entries(form.controls)) {
      if (key !== 'inherit' && key !== 'enabled') {
        setEnabled(control, !form.controls.inherit.value);
      }
    }
  };
  form.controls.inherit.valueChanges.subscribe(sync);
  sync();
  return form;
}

export type ServiceGoldenFixForm = ReturnType<typeof createServiceGoldenFixForm>;

export function toServiceGoldenFixPolicy(
  value: ReturnType<ServiceGoldenFixForm['getRawValue']>,
): GoldenFixPolicy {
  const { inherit, enabled, ...overrides } = value;
  return { enabled, ...(inherit ? NO_GOLDEN_FIX_OVERRIDES : toGoldenFixOverrides(overrides)) };
}

export function createServiceForm(s?: Partial<ServiceRequest>, defaults?: ServiceDefaults | null) {
  const folders = (values: string[] | undefined) =>
    text(
      joinLines(values),
      maxLines(30),
      eachItem(lines, FOLDER, 'One folder per line, without commas or quotes'),
      fitsColumn(lines, '\n', 2000),
    );
  const modules = (values: string[] | undefined, ...validators: ValidatorFn[]) =>
    text(
      joinLines(values),
      ...validators,
      maxLines(30),
      eachItem(lines, MODULE_FOLDER, 'Not a folder name'),
      fitsColumn(lines, '\n', 1000),
    );
  const parallel = (value: number | null | undefined) => integer(value, 1, INT_MAX);
  const node = s?.nodeBuild;

  const form = new FormGroup({
    id: new FormControl<number | null>(s?.id ?? null),
    name: text(s?.name, filled, Validators.pattern(SERVICE_NAME), unique('name', SAME_NAME)),
    description: text(s?.description, max(2000)),
    build: new FormGroup({
      tool: new FormControl<BuildTool>(s?.build?.tool ?? defaults?.buildTool ?? 'GRADLE', {
        nonNullable: true,
      }),
      sourceDir: text(s?.build?.sourceDir ?? defaults?.sourceDir ?? '.', max(500)),
      javaPath: text(s?.build?.javaPath, max(500)),
      autoSetup: flag(s?.build?.autoSetup),
      buildPath: shellSafe(s?.build?.buildPath, 500),
      command: createToolCommandForm(s?.build?.command),
    }),
    nodeBuild: new FormGroup({
      enabled: flag(node?.enabled),
      directory: text(node?.directory, relativeFolder, max(500)),
      commands: text(
        joinLines(node?.commands),
        maxLines(20, false),
        eachItem(
          tokenLines,
          COMMAND,
          'One command per line, without tabs or other control characters',
        ),
        eachItem(tokenLines, upTo(500), 'At most 500 characters per command'),
        fitsColumn(tokenLines, '\n', 4000),
      ),
      installationName: text(
        node?.installationName,
        max(200),
        notWith('nodePath', NODE_TWICE, 'enabled'),
      ),
      nodePath: text(
        node?.nodePath,
        Validators.pattern(ABSOLUTE_FOLDER),
        max(500),
        notWith('installationName', NODE_TWICE, 'enabled'),
      ),
      environment: environment(node?.environment, 2000),
      registry: url(node?.registry, 500),
      npmrcCredentialsId: text(node?.npmrcCredentialsId, max(200)),
      timeoutMinutes: integer(node?.timeoutMinutes, 1, 240),
      nexusIqScan: flag(node?.nexusIqScan !== false),
    }),
    unitTests: new FormGroup({
      command: createToolCommandForm(s?.unitTests?.command),
      resultPattern: text(s?.unitTests?.resultPattern, max(500)),
      rootDir: text(s?.unitTests?.rootDir, max(500)),
      reportOutDir: text(s?.unitTests?.reportOutDir, max(500)),
      allowEmptyResults: flag(s?.unitTests?.allowEmptyResults),
      coverageReportPath: text(s?.unitTests?.coverageReportPath, max(500)),
    }),
    tests: new FormGroup({
      maxParallel: parallel(s?.tests?.maxParallel),
      smokeMaxParallel: parallel(s?.tests?.smokeMaxParallel),
      regressionMaxParallel: parallel(s?.tests?.regressionMaxParallel),
      performanceMaxParallel: parallel(s?.tests?.performanceMaxParallel),
      smokeRequired: flag(s?.tests?.smokeRequired, true),
      regressionRequired: flag(s?.tests?.regressionRequired, true),
      performanceRequired: flag(s?.tests?.performanceRequired, true),
      smokePollIntervalSec: parallel(s?.tests?.smokePollIntervalSec),
      regressionPollIntervalSec: parallel(s?.tests?.regressionPollIntervalSec),
      performancePollIntervalSec: parallel(s?.tests?.performancePollIntervalSec),
    }),
    testJobs: new FormArray((s?.testJobs ?? []).map(createTestJobForm)),
    deployment: new FormGroup({
      target: new FormControl<DeployTarget>(
        s?.deployment?.target ?? defaults?.deployTarget ?? 'VM',
        { nonNullable: true },
      ),
      appName: shellSafe(s?.deployment?.appName, 200),
      artifactName: shellSafe(s?.deployment?.artifactName, 300),
      baseArtifactName: shellSafe(s?.deployment?.baseArtifactName, 300),
      nexusDeliveryDisabled: flag(s?.deployment?.nexusDeliveryDisabled),
    }),
    delivery: createToolCommandForm(s?.delivery),
    urbanCode: new FormGroup({
      siteName: text(s?.urbanCode?.siteName, max(200)),
      deployProcess: text(s?.urbanCode?.deployProcess, max(200)),
      skipWait: flag(s?.urbanCode?.skipWait),
      deployWithSnapshot: flag(s?.urbanCode?.deployWithSnapshot, true),
      updateSnapshotComponents: flag(s?.urbanCode?.updateSnapshotComponents),
      includeOnlyDeployVersions: flag(s?.urbanCode?.includeOnlyDeployVersions, true),
      deployOnlyChanged: flag(s?.urbanCode?.deployOnlyChanged),
      deployDescription: text(s?.urbanCode?.deployDescription, max(1000)),
      requestProperties: text(s?.urbanCode?.requestProperties, max(2000)),
    }),
    urbanCodeApplications: new FormArray(
      (s?.urbanCodeApplications ?? []).map(createUrbanCodeApplicationForm),
    ),
    sshTargets: new FormGroup({
      RD: createSshTargetForm(s?.sshTargets?.RD),
      QC: createSshTargetForm(s?.sshTargets?.QC),
    }),
    openShiftTargets: new FormGroup({
      RD: createOpenShiftTargetForm(s?.openShiftTargets?.RD, OPENSHIFT_RD_REQUIRED),
      QC: createOpenShiftTargetForm(s?.openShiftTargets?.QC),
    }),
    appScan: new FormGroup({
      applicationId: text(s?.appScan?.applicationId, filled, Validators.pattern(UUID)),
      keyId: text(s?.appScan?.keyId, filled, max(200)),
      scanName: text(s?.appScan?.scanName, Validators.pattern(SCAN_NAME), max(200)),
      includedDirs: folders(s?.appScan?.includedDirs),
      excludedDirs: folders(s?.appScan?.excludedDirs),
      compile: flag(s?.appScan?.compile, true),
      sourceCodeOnly: flag(s?.appScan?.sourceCodeOnly),
      useConfigFile: flag(s?.appScan?.useConfigFile),
      insecureTls: flag(s?.appScan?.insecureTls),
      clientPath: text(s?.appScan?.clientPath, Validators.pattern(POWERSHELL_PATH), max(500)),
      compileCommand: createToolCommandForm(s?.appScan?.compileCommand),
      dastEnabled: flag(s?.appScan?.dastEnabled),
      dastTargetUrl: url(s?.appScan?.dastTargetUrl),
      dastPresenceId: text(s?.appScan?.dastPresenceId, max(100)),
      secretCredentialsId: text(s?.appScan?.secretCredentialsId, max(200)),
    }),
    sonar: new FormGroup({
      projectName: text(s?.sonar?.projectName, max(200)),
      projectKey: text(s?.sonar?.projectKey, Validators.pattern(SONAR_KEY), max(400)),
      installationName: text(s?.sonar?.installationName, max(200)),
      credentialsId: text(s?.sonar?.credentialsId, max(200)),
      authTokenCredentialsId: text(s?.sonar?.authTokenCredentialsId, max(200)),
      badgeToken: text(
        s?.sonar?.badgeToken,
        Validators.pattern(BADGE_TOKEN),
        max(200),
        notWith('badgeCredentialsId', BADGE_TWICE),
      ),
      badgeCredentialsId: text(
        s?.sonar?.badgeCredentialsId,
        max(200),
        notWith('badgeToken', BADGE_TWICE),
      ),
      addBadges: flag(s?.sonar?.addBadges),
      fullBadges: flag(s?.sonar?.fullBadges),
      command: createToolCommandForm(s?.sonar?.command),
      serverUrl: url(s?.sonar?.serverUrl),
    }),
    nexusIq: new FormGroup({
      serverUrl: url(s?.nexusIq?.serverUrl),
      credentialsId: text(s?.nexusIq?.credentialsId, max(200)),
    }),
    nexusIqApplications: new FormArray(
      (s?.nexusIqApplications ?? []).map(createNexusIqApplicationForm),
      (control) => ((control as FormArray).length > 1 ? { rule: ONE_APPLICATION } : null),
    ),
    scm: new FormGroup({
      repositoryUrl: url(s?.scm?.repositoryUrl),
      credentialsId: text(s?.scm?.credentialsId, max(200)),
      authType: new FormControl<BitbucketAuthType>(s?.scm?.authType ?? 'BASIC', {
        nonNullable: true,
      }),
      type: new FormControl<BitbucketType | null>(s?.scm?.type ?? null),
      targetBranch: text(s?.scm?.targetBranch, max(200)),
      cloneUrl: text(s?.scm?.cloneUrl, Validators.pattern(GIT_URL), max(1000)),
      reviewers: text(
        joinWords(s?.scm?.reviewers, ', '),
        maxWords(20),
        eachItem(words, upTo(100), 'At most 100 characters per reviewer'),
        fitsColumn(words, ',', 2000),
      ),
      apiUrl: url(s?.scm?.apiUrl),
      workspace: text(s?.scm?.workspace, Validators.pattern(BITBUCKET_NAME), max(200)),
      projectKey: text(s?.scm?.projectKey, Validators.pattern(BITBUCKET_NAME), max(200)),
      repoSlug: text(s?.scm?.repoSlug, Validators.pattern(BITBUCKET_NAME), max(200)),
    }),
    goldenFix: createServiceGoldenFixForm(s?.goldenFix),
    versionCatalogs: text(
      joinLines(s?.versionCatalogs),
      maxLines(20),
      eachItem(lines, REPOSITORY_FILE, 'Name the file, such as platform/pom.xml, not its folder'),
      eachItem(
        lines,
        REPOSITORY_PATH,
        "A path relative to the repository root, without a '..' folder, with letters, digits and . _ / * + @ : = , ~ - only",
      ),
      eachItem(lines, upTo(300), 'At most 300 characters per path'),
      fitsColumn(lines, '\n', 2000),
    ),
    metrics: new FormGroup({
      enabled: flag(s?.metrics?.enabled, true),
      influxProject: text(s?.metrics?.influxProject, max(200)),
      influxEnv: text(s?.metrics?.influxEnv ?? 'test', Validators.pattern(METRICS_TAG), max(50)),
      influxUrl: url(s?.metrics?.influxUrl),
      influxCredentialsId: text(s?.metrics?.influxCredentialsId, max(200)),
    }),
    flutter: new FormGroup({
      platform: new FormControl<FlutterPlatform | null>(s?.flutter?.platform ?? null),
      modules: modules(s?.flutter?.modules, filled),
      testModules: modules(s?.flutter?.testModules, filled),
      testSubmodules: modules(s?.flutter?.testSubmodules),
      testSubplugins: modules(s?.flutter?.testSubplugins),
      signingPasswordCredentialsId: text(
        s?.flutter?.signingPasswordCredentialsId,
        filled,
        max(200),
      ),
      prodLicenseCredentialsId: text(s?.flutter?.prodLicenseCredentialsId, filled, max(200)),
      testLicenseCredentialsId: text(s?.flutter?.testLicenseCredentialsId, filled, max(200)),
      deliveryGroup: shellSafe(s?.flutter?.deliveryGroup, 200),
      deliveryArtifact: shellSafe(s?.flutter?.deliveryArtifact, 200),
      deliveryPlugin: shellSafe(s?.flutter?.deliveryPlugin, 300),
      sonarSources: text(s?.flutter?.sonarSources, max(500)),
      sonarTests: text(s?.flutter?.sonarTests, max(500)),
      sonarFlutterPlugin: flag(s?.flutter?.sonarFlutterPlugin),
      dartAnalyzeCommand: text(s?.flutter?.dartAnalyzeCommand, max(500)),
      sonarScannerVersion: text(
        s?.flutter?.sonarScannerVersion,
        Validators.pattern(SCANNER_VERSION),
        max(50),
      ),
    }),
  });

  const { build, nodeBuild, deployment, appScan, unitTests, sonar, scm, flutter } = form.controls;
  const tool = build.controls.tool;
  const target = deployment.controls.target;
  const deliveryDisabled = deployment.controls.nexusDeliveryDisabled;
  const openShift = () => target.value === 'OPENSHIFT';
  const vm = () => target.value === 'VM';
  build.controls.command.controls.tasks.addValidators(filled);
  form.controls.delivery.controls.tasks.addValidators(filled);
  requireOnceUsed(form.controls.openShiftTargets.controls.QC);
  revalidateOnChange(sonar.controls.badgeToken, sonar.controls.badgeCredentialsId);
  revalidateOnChange(sonar.controls.badgeCredentialsId, sonar.controls.badgeToken);
  revalidateOnChange(nodeBuild.controls.installationName, nodeBuild.controls.nodePath);
  revalidateOnChange(nodeBuild.controls.nodePath, nodeBuild.controls.installationName);
  requireWhile(
    build.controls.javaPath,
    () => tool.value === 'FLUTTER' || !build.controls.autoSetup.value,
    tool,
    build.controls.autoSetup,
  );
  requireWhile(
    build.controls.buildPath,
    () => vm() && tool.value === 'MAVEN' && !deliveryDisabled.value,
    tool,
    target,
    deliveryDisabled,
  );
  [
    flutter.controls.deliveryGroup,
    flutter.controls.deliveryArtifact,
    flutter.controls.deliveryPlugin,
  ].forEach((control) => requireWhile(control, vm, target));
  requireWhile(deployment.controls.appName, openShift, target);
  requireWhile(deployment.controls.artifactName, openShift, target);
  requireWhile(
    appScan.controls.dastTargetUrl,
    () => appScan.controls.dastEnabled.value,
    appScan.controls.dastEnabled,
  );
  requireWhile(
    unitTests.controls.command.controls.tasks,
    () => unitTestsConfigured(form),
    unitTests,
  );
  requireWhile(
    sonar.controls.command.controls.tasks,
    () => !!optional(sonar.controls.projectKey.value) || commandEntered(sonar.controls.command),
    sonar.controls.projectKey,
    sonar.controls.command,
  );
  requireWhile(
    appScan.controls.compileCommand.controls.tasks,
    () => commandEntered(appScan.controls.compileCommand),
    appScan.controls.compileCommand,
  );
  requireWhile(
    scm.controls.credentialsId,
    () => !!optional(scm.controls.repositoryUrl.value),
    scm.controls.repositoryUrl,
  );
  [
    build.controls.command.controls.tasks,
    form.controls.delivery.controls.tasks,
    nodeBuild.controls.installationName,
    nodeBuild.controls.nodePath,
  ].forEach((control) => control.updateValueAndValidity());
  const sources: AbstractControl[] = [
    tool,
    target,
    deliveryDisabled,
    appScan.controls.compile,
    appScan.controls.dastEnabled,
    nodeBuild.controls.enabled,
  ];
  sources.forEach((control) => control.valueChanges.subscribe(() => syncApplicability(form)));
  syncApplicability(form);
  return form;
}

export type ServiceForm = ReturnType<typeof createServiceForm>;

export function followToolDefaults(
  service: ServiceForm,
  template: ServiceTemplateValues | null,
  destroyRef?: DestroyRef,
): void {
  const { tool, buildPath, command, autoSetup } = service.controls.build.controls;
  tool.valueChanges
    .pipe(startWith(tool.value), pairwise(), takeUntilDestroyed(destroyRef))
    .subscribe(([previous, next]) => {
      const before = buildDefaults(template, previous);
      const after = buildDefaults(template, next);
      if (!buildPath.value || buildPath.value === before.artifact) {
        buildPath.setValue(after.artifact);
      }
      if (!command.controls.tasks.value || command.controls.tasks.value === before.tasks) {
        command.controls.tasks.setValue(after.tasks);
      }
      if (service.controls.id.value !== null) {
        return;
      }
      if (previous === 'FLUTTER' && next !== 'FLUTTER' && autoSetup.pristine) {
        autoSetup.setValue(true);
      }
      const patterns = service.controls.nexusIqApplications.controls[0]?.controls.scanPatterns;
      if (patterns?.value === before.scanPattern && after.scanPattern) {
        patterns.setValue(after.scanPattern);
      }
    });
}

const NEXUS_IQ_APPLICATION = 'nexusIqApplications.0.application';
const FILLED_FROM_NAME = [
  'scm.repositoryUrl',
  'scm.credentialsId',
  'deployment.appName',
  NEXUS_IQ_APPLICATION,
];

interface Naming {
  code: string;
  name: string;
}

/**
 * Lets a new service follow its name and its product code through the service template: it fills an empty repository
 * URL, Bitbucket credentials ID, application name or Nexus IQ application with its scan pattern, and replaces every value
 * that still holds what the previous name gave, such as the image repositories of a copy of the service named copiedFrom.
 */
export function followName(
  service: ServiceForm,
  template: ServiceTemplateValues | null,
  productCode: Observable<string>,
  destroyRef: DestroyRef,
  copiedFrom = '',
): void {
  const name = service.controls.name;
  const apps = service.controls.nexusIqApplications;
  let named: Naming | null = null;
  let offered = apps.length > 0;
  combineLatest([productCode, name.valueChanges.pipe(startWith(name.value))])
    .pipe(
      map(([code, typed]) => ({ code: code.trim(), name: typed.trim() })),
      filter((next) => next.name !== ''),
      takeUntilDestroyed(destroyRef),
    )
    .subscribe((next) => {
      rename(service, template, named ?? { ...next, name: copiedFrom }, next, offered);
      named = next;
      offered ||= apps.length > 0;
    });
}

/** Renames the values the name gives; a Nexus IQ application is offered once, so one the user removed stays away. */
function rename(
  service: ServiceForm,
  template: ServiceTemplateValues | null,
  previous: Naming,
  next: Naming,
  offered: boolean,
): void {
  const before = namedValues(service, template, previous);
  const after = namedValues(service, template, next);
  const application = after[NEXUS_IQ_APPLICATION];
  if (application) {
    const { scanPattern } = buildDefaults(template, service.controls.build.controls.tool.value);
    const apps = service.controls.nexusIqApplications;
    const first = apps.at(0);
    if (!first && !offered) {
      apps.push(nexusIqApplicationForm(application, scanPattern));
    } else if (first && !first.controls.scanPatterns.value && scanPattern) {
      first.controls.scanPatterns.setValue(scanPattern);
    }
  }
  for (const [path, value] of Object.entries(after)) {
    const control = service.get(path);
    const current = String(control?.value ?? '').trim();
    if (
      control &&
      value &&
      (current === before[path] || (!current && FILLED_FROM_NAME.includes(path)))
    ) {
      control.setValue(value);
    }
  }
}

function namedValues(
  service: ServiceForm,
  template: ServiceTemplateValues | null,
  { code, name }: Naming,
): Record<string, string> {
  if (!name) {
    return {};
  }
  const named = namedDefaults({ template, productCode: code }, name);
  const values = {
    'scm.repositoryUrl': named.repositoryUrl,
    'scm.credentialsId': named.repositoryUrl && (template?.bitbucketCredentialsId ?? ''),
    [NEXUS_IQ_APPLICATION]: named.nexusIqApplication,
  };
  if (service.controls.deployment.controls.target.value !== 'OPENSHIFT') {
    return values;
  }
  const rd = service.controls.openShiftTargets.controls.RD.controls;
  const project = rd.projectBuild.value.trim().replace(/-build$/, '');
  const image = imageRepository(project, name, template);
  return {
    ...values,
    'deployment.appName': name,
    ...(project && {
      'openShiftTargets.RD.dockerRepoPush': image,
      'openShiftTargets.RD.dockerRepoPull': image,
      'openShiftTargets.QC.dockerRepoPull': image,
    }),
  };
}

export function unitTestsConfigured(form: ServiceForm): boolean {
  const v = form.controls.unitTests.getRawValue();
  return (
    commandEntered(form.controls.unitTests.controls.command) ||
    [v.resultPattern, v.rootDir, v.reportOutDir].some((value) => !!optional(value)) ||
    v.allowEmptyResults
  );
}

function commandEntered(command: ToolCommandForm): boolean {
  return Object.values(command.getRawValue()).some((value) =>
    typeof value === 'string' ? !!optional(value) : value,
  );
}

function syncApplicability(form: ServiceForm): void {
  const tool = form.controls.build.controls.tool.value;
  const deployment = form.controls.deployment.controls;
  const vm = deployment.target.value === 'VM';
  const delivers = !deployment.nexusDeliveryDisabled.value;
  const flutter = tool === 'FLUTTER';
  setEnabled(form.controls.build.controls.command, !flutter);
  if (flutter) {
    form.controls.build.controls.autoSetup.setValue(false);
  }
  setEnabled(form.controls.build.controls.autoSetup, !flutter);
  const node = form.controls.nodeBuild;
  if (flutter && node.controls.enabled.value) {
    node.controls.enabled.setValue(false);
  }
  setEnabled(node, !flutter);
  Object.entries(node.controls).forEach(
    ([key, control]) => key === 'enabled' || setEnabled(control, node.controls.enabled.value),
  );
  setEnabled(form.controls.unitTests.controls.command, !flutter);
  setEnabled(form.controls.sonar.controls.command, !flutter);
  const appScan = form.controls.appScan.controls;
  setEnabled(appScan.compileCommand, !flutter && appScan.compile.value);
  [appScan.dastTargetUrl, appScan.dastPresenceId].forEach((control) =>
    setEnabled(control, appScan.dastEnabled.value),
  );
  setEnabled(form.controls.delivery, vm && tool === 'MAVEN' && delivers);
  toolCommands(form).forEach((command) =>
    setEnabled(command.controls.mavenHome, command.enabled && tool === 'MAVEN'),
  );
  setEnabled(form.controls.urbanCode, vm);
  setEnabled(form.controls.urbanCodeApplications, vm);
  setEnabled(form.controls.sshTargets, vm);
  setEnabled(form.controls.openShiftTargets, !vm);
  setEnabled(form.controls.flutter, flutter);
  [deployment.artifactName, deployment.baseArtifactName].forEach((control) =>
    setEnabled(control, delivers),
  );
  OPENSHIFT_IMAGE_DELIVERY.forEach((key) =>
    setEnabled(form.controls.openShiftTargets.controls.RD.controls[key], !vm && delivers),
  );
  const f = form.controls.flutter.controls;
  [f.deliveryGroup, f.deliveryArtifact, f.deliveryPlugin].forEach((control) =>
    setEnabled(control, flutter && delivers),
  );
}

function toolCommands(form: ServiceForm): ToolCommandForm[] {
  const c = form.controls;
  return [
    c.build.controls.command,
    c.unitTests.controls.command,
    c.sonar.controls.command,
    c.appScan.controls.compileCommand,
    c.delivery,
  ];
}

function kept(control: FormControl<string>, value = control.value): string | null {
  return passesValidators(control) ? optional(value) : null;
}

export const SAME_NAME = 'another service of this product already uses this name';
export const ONE_APPLICATION = 'A service scans one Nexus IQ application: remove the others';
export const BADGE_TWICE = 'Give the badge token or its credentials ID, not both';
export const NODE_TWICE = 'Give the Node.js installation or its home on the agent, not both';
export const PRODUCT_WIDE_FIELD = /^services\[\d+]\.name$/;

const valueAt = (item: AbstractControl, key: string) =>
  String(item.get(key)?.value ?? '')
    .trim()
    .toLowerCase() || null;

function unique(key: string, message: string): ValidatorFn {
  return (control) => {
    const item = control.parent;
    const items = item?.parent;
    if (!item || !(items instanceof FormArray)) {
      return null;
    }
    const own = valueAt(item, key);
    const earlier = items.controls.slice(0, items.controls.indexOf(item));
    return own !== null && earlier.some((other) => valueAt(other, key) === own)
      ? { rule: message }
      : null;
  };
}

/**
 * Refuses a value next to one in the other control; with a switch, only while that switch is on. A Library default
 * the user left as prefilled is no value of the service: it is sent as null.
 */
function notWith(other: string, message: string, whileOn?: string): ValidatorFn {
  const given = (control: AbstractControl | null | undefined) =>
    !!control && !isUntouchedDefault(control) && !!optional(String(control.value ?? ''));
  return (control) =>
    (!whileOn || control.parent?.get(whileOn)?.value) &&
    given(control) &&
    given(control.parent?.get(other))
      ? { rule: message }
      : null;
}

function revalidateUniqueValues(form: ProductForm): void {
  form.controls.services.controls.forEach((service) => revalidate(service.controls.name));
}

function revalidate(control: AbstractControl): void {
  const status = control.status;
  control.updateValueAndValidity({ emitEvent: false });
  if (control.status !== status) {
    control.updateValueAndValidity({ onlySelf: true });
  }
}

export function applyProductProblems(form: ProductForm, problems: FieldProblem[]): FieldProblem[] {
  return applyFieldProblems(form, problems, (field) =>
    PRODUCT_WIDE_FIELD.test(field) ? form : null,
  );
}

export function createProductForm() {
  const form = new FormGroup({
    code: text('', filled, Validators.pattern(PRODUCT_CODE)),
    name: text('', filled, max(200)),
    description: text('', max(4000)),
    ownerTeam: text('', max(200)),
    contactEmail: text('', Validators.email, max(320)),
    departmentId: new FormControl<number | null>(null, Validators.required),
    agentLabels: agentLabels('', false),
    services: new FormArray<ServiceForm>([]),
  });
  form.controls.agentLabels.disable();
  upperCaseAsTyped(form.controls.code);
  form.valueChanges.subscribe(() => revalidateUniqueValues(form));
  return form;
}

function upperCaseAsTyped(control: FormControl<string>): void {
  control.valueChanges.subscribe((value) => {
    const upper = value.toUpperCase();
    if (upper !== value) {
      control.setValue(upper, { emitEvent: false });
    }
  });
}

export type ProductForm = ReturnType<typeof createProductForm>;

export function patchProduct(form: ProductForm, product: Product): void {
  form.patchValue({
    code: product.code,
    name: product.name,
    description: product.description ?? '',
    ownerTeam: product.ownerTeam ?? '',
    contactEmail: product.contactEmail ?? '',
    departmentId: product.departmentId,
  });
  form.controls.services.clear();
  product.services.forEach((service) => form.controls.services.push(createServiceForm(service)));
}

/** The settings of a service without what identifies it: its name, projects, scan name and deployment name. */
export function copiedService(source: Partial<ServiceRequest>): Partial<ServiceRequest> {
  return {
    ...source,
    id: null,
    name: '',
    appScan: source.appScan && { ...source.appScan, scanName: null },
    deployment: source.deployment && { ...source.deployment, appName: null },
    sonar: source.sonar && { ...source.sonar, projectKey: null, projectName: null },
    metrics: source.metrics && { ...source.metrics, influxProject: null },
    nexusIqApplications: source.nexusIqApplications?.map((app) => ({ ...app, application: '' })),
  };
}

export function duplicateService(
  source: ServiceForm,
  settings: GlobalSettings | null,
): ServiceForm {
  const copy = createServiceForm(copiedService(toServiceRequest(source)));
  copy.controls.name.setValue(`${source.controls.name.value}-copy`.slice(0, 100));
  applyLibraryDefaults(copy, settings);
  return copy;
}

export type AppScanKey = Partial<Pick<AppScanSettings, 'keyId' | 'secretCredentialsId'>>;

export function prefillNewService(
  form: ServiceForm,
  settings: GlobalSettings | null,
  keys: readonly AppScanKey[],
): void {
  const { keyId, secretCredentialsId } = form.controls.appScan.controls;
  const key = keys.find((candidate) => optional(candidate.keyId));
  if (key && !optional(keyId.value)) {
    keyId.setValue(key.keyId!.trim());
    if (!optional(secretCredentialsId.value)) {
      secretCredentialsId.setValue(key.secretCredentialsId ?? '');
    }
  }
  applyLibraryDefaults(form, settings);
}

export function toProductRequest(form: ProductForm, version: number | null): ProductRequest {
  const value = form.getRawValue();
  return {
    code: value.code.trim(),
    name: value.name.trim(),
    description: optional(value.description),
    ownerTeam: optional(value.ownerTeam),
    contactEmail: optional(value.contactEmail),
    departmentId: value.departmentId,
    version,
    services: form.controls.services.controls.map(toServiceRequest),
    ...(form.controls.agentLabels.enabled ? { agentLabels: commaItems(value.agentLabels) } : {}),
  };
}

export function toServiceRequest(form: ServiceForm): ServiceRequest {
  const c = form.controls;
  const v = withoutUntouchedDefaults(form);
  const tool = v.build.tool;
  const flutter = tool === 'FLUTTER';
  const vm = v.deployment.target === 'VM';
  const command = (group: ToolCommandForm, applies: boolean) =>
    applies ? toToolCommand(group) : { ...NO_COMMAND };
  return {
    id: v.id,
    name: v.name.trim(),
    description: optional(v.description),
    build: {
      ...sent(v.build),
      sourceDir: optional(v.build.sourceDir) ?? '.',
      command: command(c.build.controls.command, !flutter),
    },
    nodeBuild: toNodeBuild(c.nodeBuild, v.nodeBuild),
    unitTests: {
      ...sent(v.unitTests),
      command: command(c.unitTests.controls.command, !flutter),
    },
    tests: { ...v.tests },
    testJobs: c.testJobs.controls.map(toTestJob),
    deployment: sent(v.deployment),
    delivery: command(c.delivery, vm && tool === 'MAVEN'),
    urbanCode: sent(v.urbanCode),
    urbanCodeApplications: vm ? c.urbanCodeApplications.controls.map(toUrbanCodeApplication) : [],
    sshTargets: vm ? regionTargets(v.sshTargets) : {},
    openShiftTargets: vm ? {} : regionTargets(v.openShiftTargets),
    appScan: {
      ...sent(v.appScan),
      includedDirs: lines(v.appScan.includedDirs),
      excludedDirs: lines(v.appScan.excludedDirs),
      compileCommand: command(
        c.appScan.controls.compileCommand,
        !flutter && passesValidators(c.appScan.controls.compileCommand),
      ),
      dastTargetUrl: kept(c.appScan.controls.dastTargetUrl),
      dastPresenceId: kept(c.appScan.controls.dastPresenceId),
    },
    sonar: {
      ...sent(v.sonar),
      command: command(c.sonar.controls.command, !flutter),
    },
    nexusIq: sent(v.nexusIq),
    nexusIqApplications: v.nexusIqApplications.map((application) => ({
      ...sent(application),
      scanPatterns: lines(application.scanPatterns),
      stage: optional(application.stage) ?? 'build',
    })),
    scm: {
      ...sent(v.scm),
      reviewers: words(v.scm.reviewers),
    },
    goldenFix: toServiceGoldenFixPolicy(v.goldenFix),
    versionCatalogs: passesValidators(c.versionCatalogs) ? relativePaths(v.versionCatalogs) : [],
    metrics: sent(v.metrics),
    flutter: flutter ? toFlutterSettings(v.flutter) : null,
  };
}

/** The Node.js build as the form value holds it, without what fails its rules while hidden. */
function toNodeBuild(
  group: ServiceForm['controls']['nodeBuild'],
  v: ReturnType<ServiceForm['controls']['nodeBuild']['getRawValue']>,
): NodeBuildSettings {
  const c = group.controls;
  const listed = (control: FormControl<string>, value: string) =>
    passesValidators(control) ? lines(value, false) : [];
  return {
    enabled: v.enabled,
    directory: relativePath(kept(c.directory, v.directory)),
    commands: listed(c.commands, v.commands),
    installationName: kept(c.installationName, v.installationName),
    nodePath: kept(c.nodePath, v.nodePath),
    environment: listed(c.environment, v.environment),
    registry: kept(c.registry, v.registry),
    npmrcCredentialsId: kept(c.npmrcCredentialsId, v.npmrcCredentialsId),
    timeoutMinutes: passesValidators(c.timeoutMinutes) ? v.timeoutMinutes : null,
    nexusIqScan: v.nexusIqScan ? null : false,
  };
}

function toFlutterSettings(
  v: ReturnType<ServiceForm['controls']['flutter']['getRawValue']>,
): FlutterSettings {
  return {
    ...sent(v),
    modules: lines(v.modules),
    testModules: lines(v.testModules),
    testSubmodules: lines(v.testSubmodules),
    testSubplugins: lines(v.testSubplugins),
  };
}

function regionTargets<T extends object>(
  targets: Record<Region, T>,
): Partial<Record<Region, Sent<T>>> {
  const result: Partial<Record<Region, Sent<T>>> = {};
  for (const region of REGIONS) {
    const target = sent(targets[region]);
    if (Object.values(target as object).some((value) => value !== null && value !== false)) {
      result[region] = target;
    }
  }
  return result;
}

export function firstServiceWithProblem(problems: FieldProblem[]): number | null {
  const indexes = problems
    .map((problem) => /^services\[(\d+)]/.exec(problem.field))
    .filter((match): match is RegExpExecArray => match !== null)
    .map((match) => Number(match[1]));
  return indexes.length ? Math.min(...indexes) : null;
}

export type ServiceSectionId =
  | 'general'
  | 'build'
  | 'unitTests'
  | 'testJobs'
  | 'deployment'
  | 'urbanCode'
  | 'ssh'
  | 'openShift'
  | 'appScan'
  | 'sonar'
  | 'nexusIq'
  | 'scm'
  | 'goldenFix'
  | 'metrics'
  | 'flutter';

type ServiceControlKey = keyof ServiceForm['controls'];

export interface ServiceSection {
  id: ServiceSectionId;
  label: string;
  icon: string;
  keys: ServiceControlKey[];
  applies?: (tool: BuildTool, target: DeployTarget) => boolean;
}

const onVm: ServiceSection['applies'] = (_, target) => target === 'VM';
const onOpenShift: ServiceSection['applies'] = (_, target) => target === 'OPENSHIFT';
const onFlutter: ServiceSection['applies'] = (tool) => tool === 'FLUTTER';

const section = (
  id: ServiceSectionId,
  label: string,
  icon: string,
  keys: ServiceControlKey[],
  applies?: ServiceSection['applies'],
): ServiceSection => ({ id, label, icon, keys, ...(applies ? { applies } : {}) });

export const SERVICE_SECTIONS: ServiceSection[] = [
  section('general', 'General', 'badge', ['name', 'description']),
  section('build', 'Build', 'build', ['build', 'nodeBuild']),
  section('unitTests', 'Unit tests and coverage', 'fact_check', ['unitTests']),
  section('testJobs', 'Test jobs', 'science', ['tests', 'testJobs']),
  section('deployment', 'Deployment', 'rocket_launch', ['deployment', 'delivery']),
  section('urbanCode', 'UrbanCode Deploy', 'hub', ['urbanCode', 'urbanCodeApplications'], onVm),
  section('ssh', 'SSH targets', 'dns', ['sshTargets'], onVm),
  section('openShift', 'OpenShift targets', 'cloud', ['openShiftTargets'], onOpenShift),
  section('appScan', 'AppScan SAST and DAST', 'security', ['appScan']),
  section('sonar', 'SonarQube', 'analytics', ['sonar']),
  section('nexusIq', 'Nexus IQ', 'inventory', ['nexusIq', 'nexusIqApplications']),
  section('scm', 'Bitbucket', 'merge', ['scm']),
  section('goldenFix', 'GoldenFix', 'auto_fix_high', ['goldenFix', 'versionCatalogs']),
  section('metrics', 'Monitoring', 'insights', ['metrics']),
  section('flutter', 'Flutter', 'phone_iphone', ['flutter'], onFlutter),
];

export function visibleSections(form: ServiceForm): ServiceSection[] {
  const tool = form.controls.build.controls.tool.value;
  const target = form.controls.deployment.controls.target.value;
  return SERVICE_SECTIONS.filter((section) => section.applies?.(tool, target) ?? true);
}

export function visibleSectionsForPipeline(
  form: ServiceForm,
  pipeline?: PipelineType | null,
): ServiceSection[] {
  const all = visibleSections(form);
  if (!pipeline || pipeline === 'FULL') {
    return all;
  }
  const allowed = new Set<ServiceSectionId>();
  allowed.add('general');
  allowed.add('build');
  if (form.controls.build.controls.tool.value === 'FLUTTER') {
    allowed.add('flutter');
  }

  switch (pipeline) {
    case 'NEXUS_IQ':
      allowed.add('appScan');
      allowed.add('nexusIq');
      allowed.add('scm');
      allowed.add('goldenFix');
      break;
    case 'SAST':
      allowed.add('appScan');
      allowed.add('scm');
      break;
    case 'SECURITY':
      allowed.add('unitTests');
      allowed.add('deployment');
      allowed.add('openShift');
      allowed.add('appScan');
      allowed.add('sonar');
      allowed.add('nexusIq');
      allowed.add('scm');
      allowed.add('goldenFix');
      allowed.add('metrics');
      break;
    default:
      return all;
  }
  return all.filter((s) => allowed.has(s.id));
}

export function sectionInvalid(form: ServiceForm, section: ServiceSection): boolean {
  return section.keys.some((key) => form.controls[key].invalid);
}

export function sectionTouched(form: ServiceForm, section: ServiceSection): boolean {
  return section.keys.some((key) => form.controls[key].touched);
}

export function firstInvalidSection(
  form: ServiceForm,
  pipeline?: PipelineType | null,
): ServiceSectionId | null {
  return (
    visibleSectionsForPipeline(form, pipeline).find((section) => sectionInvalid(form, section))
      ?.id ?? null
  );
}
