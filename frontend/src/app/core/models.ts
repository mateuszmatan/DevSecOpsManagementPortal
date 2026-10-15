import { Department as PortalDepartment } from '@common/core/models';

export type BuildTool = 'GRADLE' | 'MAVEN' | 'FLUTTER';
export type DeployTarget = 'VM' | 'OPENSHIFT';
export type PipelineType = 'FULL' | 'SECURITY' | 'EXTENDED' | 'SAST' | 'NEXUS_IQ';
export type KeyStatus = 'ACTIVE' | 'REVOKED';
export type RunResult =
  'SUCCESS' | 'UNSTABLE' | 'FAILURE' | 'ABORTED' | 'NOT_BUILT' | 'NO_DATA' | 'DISABLED';
export type DoraLevel = 'ELITE' | 'HIGH' | 'MEDIUM' | 'LOW';
export type TestStage = 'SMOKE' | 'REGRESSION' | 'PERFORMANCE';
export type TestJobType = 'LOCAL' | 'REMOTE';
export type Region = 'RD' | 'QC';
export type BitbucketAuthType = 'BASIC' | 'BEARER';
export type BitbucketType = 'SERVER' | 'CLOUD';
export const FLUTTER_PLATFORMS = [
  'APK',
  'APPBUNDLE',
  'IOS',
  'MACOS',
  'LINUX',
  'WINDOWS',
  'WEB',
] as const;
export type FlutterPlatform = (typeof FLUTTER_PLATFORMS)[number];
export type Scanner = 'SAST' | 'SCA' | 'NEXUS_IQ' | 'DAST';

export const REGIONS: Region[] = ['RD', 'QC'];
export const TEST_STAGES: TestStage[] = ['SMOKE', 'REGRESSION', 'PERFORMANCE'];
export type TestSuite = 'UNIT' | TestStage;
export const TEST_SUITES: TestSuite[] = ['UNIT', ...TEST_STAGES];
export const SCANNERS: Scanner[] = ['SAST', 'SCA', 'NEXUS_IQ', 'DAST'];

export interface Department extends PortalDepartment {
  serviceCount: number;
  pipelineCount: number;
  activePipelineCount: number;
}

export interface ProductSummary {
  id: number;
  code: string;
  name: string;
  description: string | null;
  ownerTeam: string | null;
  departmentId: number | null;
  departmentName: string | null;
  serviceCount: number;
  pipelineCount: number;
  activePipelineCount: number;
  updatedAt: string;
}

export interface ToolCommand {
  tasks: string[];
  flags: string[];
  directory: string | null;
  mavenHome: string | null;
  environment: string[];
  label: string | null;
  returnStdout: boolean;
}

export interface BuildSettings {
  tool: BuildTool;
  sourceDir: string;
  javaPath: string | null;
  autoSetup: boolean;
  buildPath: string | null;
  command: ToolCommand;
}

export interface UnitTestSettings {
  command: ToolCommand;
  resultPattern: string | null;
  rootDir: string | null;
  reportOutDir: string | null;
  allowEmptyResults: boolean;
  coverageReportPath: string | null;
}

export interface TestSettings {
  maxParallel: number | null;
  smokeMaxParallel: number | null;
  regressionMaxParallel: number | null;
  performanceMaxParallel: number | null;
  smokeRequired: boolean;
  regressionRequired: boolean;
  performanceRequired: boolean;
  smokePollIntervalSec: number | null;
  regressionPollIntervalSec: number | null;
  performancePollIntervalSec: number | null;
}

export interface TestJob {
  stage: TestStage;
  name: string | null;
  type: TestJobType | null;
  job: string;
  timeoutMinutes: number | null;
  parameters: string | null;
  remoteJenkins: string | null;
  remoteJenkinsUrl: string | null;
  credentialsId: string | null;
  pollIntervalSec: number | null;
  tokenCredentialsId: string | null;
  abortTriggeredJob: boolean;
  overrideTrustAllCertificates: boolean;
  preventRemoteBuildQueue: boolean;
  trustAllCertificates: boolean;
  useCrumbCache: boolean;
  useJobInfoCache: boolean;
}

export interface DeploymentSettings {
  target: DeployTarget;
  appName: string | null;
  artifactName: string | null;
  baseArtifactName: string | null;
}

export interface UrbanCodeSettings {
  siteName: string | null;
  deployProcess: string | null;
  skipWait: boolean;
  deployWithSnapshot: boolean;
  updateSnapshotComponents: boolean;
  includeOnlyDeployVersions: boolean;
  deployOnlyChanged: boolean;
  deployDescription: string | null;
  requestProperties: string | null;
}

export interface UrbanCodeComponent {
  componentName: string;
  baseDir: string | null;
  fileIncludePatterns: string | null;
  fileExcludePatterns: string | null;
  versionPrefix: string | null;
  version: string | null;
  incrementalVersion: boolean;
  extensions: string | null;
  charset: string | null;
  pushDescription: string | null;
  versionProperties: string | null;
  versionDescription: string | null;
}

export interface UrbanCodeApplicationSettings {
  applicationName: string;
  order: number | null;
  environments: string[];
  snapshotName: string | null;
  siteName: string | null;
  deployProcess: string | null;
  skipWait: boolean | null;
  deployWithSnapshot: boolean | null;
  updateSnapshotComponents: boolean | null;
  includeOnlyDeployVersions: boolean | null;
  deployOnlyChanged: boolean | null;
  deployDescription: string | null;
  description: string | null;
  requestProperties: string | null;
  components: UrbanCodeComponent[];
}

export interface SshTarget {
  host: string | null;
  user: string | null;
  deployDir: string | null;
  deployScript: string | null;
  versionFile: string | null;
}

export interface OpenShiftTarget {
  projectBuild: string | null;
  buildConfigPath: string | null;
  dockerFilePath: string | null;
  buildContext: string | null;
  addFile: string | null;
  dockerRepoPush: string | null;
  dockerRepoPull: string | null;
  certDir: string | null;
  nexusAuthFile: string | null;
  projectDeployment: string | null;
  deployConfigPath: string | null;
  configPath: string | null;
  skipConfigDeploy: boolean;
  healthCheckUrl: string | null;
  routeHostname: string | null;
  deploymentPath: string | null;
  deploymentRepoUrl: string | null;
  deploymentRepoBranch: string | null;
  deploymentRepoCredentialsId: string | null;
  buildTag: string | null;
  internalDockerUrl: string | null;
}

export interface AppScanSettings {
  applicationId: string;
  sastScanName: string | null;
  includedDirs: string[];
  excludedDirs: string[];
  compile: boolean;
  sourceCodeOnly: boolean;
  useConfigFile: boolean;
  insecureTls: boolean;
  clientPath: string | null;
  compileCommand: ToolCommand;
  dastEnabled: boolean;
  dastScanName: string | null;
  dastTargetUrl: string | null;
  dastPresenceId: string | null;
  secretCredentialsId: string | null;
}

export interface SonarSettings {
  projectName: string | null;
  projectKey: string | null;
  installationName: string | null;
  credentialsId: string | null;
  authTokenCredentialsId: string | null;
  badgeToken: string | null;
  addBadges: boolean;
  fullBadges: boolean;
  command: ToolCommand;
  serverUrl: string | null;
}

export interface NexusIqSettings {
  serverUrl: string | null;
  credentialsId: string | null;
  scaScanName: string | null;
}

export interface NexusIqApplication {
  application: string;
  scanPatterns: string[];
  stage: string;
  failOnNetworkError: boolean;
}

export interface ScmSettings {
  repositoryUrl: string | null;
  credentialsId: string | null;
  authType: BitbucketAuthType;
  type: BitbucketType | null;
  targetBranch: string | null;
  cloneUrl: string | null;
  reviewers: string[];
  apiUrl: string | null;
  workspace: string | null;
  projectKey: string | null;
  repoSlug: string | null;
}

export interface GoldenFixPolicy {
  enabled: boolean | null;
  onlyDirectDependencies: boolean | null;
  minThreatLevel: number | null;
  ecosystems: string[];
  goldenVersionTypes: string[];
  excludeDirs: string[];
  verifyEnabled: boolean | null;
  verifyMaxAttempts: number | null;
  verifyTimeoutMinutes: number | null;
  verifyMavenCommand: string | null;
  verifyGradleCommand: string | null;
  verifyNpmCommand: string | null;
  verifyPipCommand: string | null;
  verifyPubCommand: string | null;
  commitAuthorName: string | null;
  commitAuthorEmail: string | null;
  timeZone: string | null;
}

export interface GlobalGoldenFixPolicy extends GoldenFixPolicy {
  enabled: boolean;
}

export interface MetricsSettings {
  enabled: boolean;
  influxProject: string | null;
  influxEnv: string | null;
  influxUrl: string | null;
  influxCredentialsId: string | null;
}

export interface FlutterSettings {
  platform: FlutterPlatform | null;
  modules: string[];
  testModules: string[];
  testSubmodules: string[];
  testSubplugins: string[];
  signingPasswordCredentialsId: string | null;
  prodLicenseCredentialsId: string | null;
  testLicenseCredentialsId: string | null;
  deliveryGroup: string | null;
  deliveryArtifact: string | null;
  deliveryPlugin: string | null;
  sonarSources: string | null;
  sonarTests: string | null;
  sonarFlutterPlugin: boolean;
  dartAnalyzeCommand: string | null;
  sonarScannerVersion: string | null;
}

export interface ServiceSettings {
  build: BuildSettings;
  unitTests: UnitTestSettings;
  tests: TestSettings;
  testJobs: TestJob[];
  deployment: DeploymentSettings;
  delivery: ToolCommand;
  urbanCode: UrbanCodeSettings;
  urbanCodeApplications: UrbanCodeApplicationSettings[];
  sshTargets: Partial<Record<Region, SshTarget>>;
  openShiftTargets: Partial<Record<Region, OpenShiftTarget>>;
  appScan: AppScanSettings;
  sonar: SonarSettings;
  nexusIq: NexusIqSettings;
  nexusIqApplications: NexusIqApplication[];
  scm: ScmSettings;
  goldenFix: GoldenFixPolicy;
  metrics: MetricsSettings;
  flutter: FlutterSettings | null;
}

export interface Service extends ServiceSettings {
  id: number;
  name: string;
  description: string | null;
}

export interface ServiceRequest extends ServiceSettings {
  id: number | null;
  name: string;
  description: string | null;
}

export interface AppScanAccount {
  keyId: string;
  secretCredentialsId: string | null;
}

export interface ProductFields {
  code: string;
  name: string;
  description: string | null;
  ownerTeam: string | null;
  contactEmail: string | null;
  departmentId: number | null;
  appScan: AppScanAccount | null;
}

export interface Product extends ProductFields {
  id: number;
  version: number;
  updatedAt: string;
  services: Service[];
}

export interface ProductRequest extends ProductFields {
  version: number | null;
  services: ServiceRequest[];
}

export interface PipelineKey {
  id: number;
  value: string | null;
  hint: string;
  status: KeyStatus;
  issuedAt: string;
  revokedAt: string | null;
  revokeReason: string | null;
  lastUsedAt: string | null;
}

export interface Pipeline {
  id: number;
  productId: number;
  productCode: string;
  productName: string;
  serviceId: number;
  serviceName: string;
  type: PipelineType;
  entryPoint: string;
  agentLabels: string[];
  extendedPipelineJob: string | null;
  securityPipelineJob: string | null;
  jenkinsJob: string | null;
  jenkinsJobUrl: string | null;
  description: string | null;
  enabled: boolean;
  activeKey: PipelineKey | null;
  influxProjectTag: string;
  influxEnv: string;
  updatedAt: string;
  keys: PipelineKey[] | null;
  version: number;
}

export interface PipelineRequest {
  type: PipelineType;
  agentLabels: string[];
  extendedPipelineJob: string | null;
  securityPipelineJob: string | null;
  jenkinsJob: string | null;
  description: string | null;
  version: number | null;
}

export interface ServicePipelines {
  serviceId: number;
  serviceName: string;
  description: string | null;
  buildTool: BuildTool;
  deployTarget: DeployTarget;
  pipelines: Pipeline[];
}

export interface PlatformSettings {
  jenkinsUrl: string | null;
  jenkinsLibrary: string;
  asocUrl: string;
  appScanClientLinuxUrl: string;
  appScanClientWindowsUrl: string;
  proxyHost: string | null;
  proxyPort: number | null;
  proxyUser: string | null;
  oisHost: string | null;
  sonarServerUrl: string;
  sonarInstallationName: string;
  nexusIqServerUrl: string;
  nexusIqCredentialsId: string;
  nexusSnapshotRepositoryUrl: string | null;
  nexusSnapshotRepositoryId: string | null;
  influxWriteUrl: string | null;
  influxCredentialsId: string | null;
  iosBuildAgent: string | null;
}

export interface DeploymentDefaults {
  urbanCodeSiteName: string;
  urbanCodeDeployProcess: string;
  rdHost: string;
  qcHost: string;
  sshUser: string;
  deployScript: string;
  versionFile: string;
}

export interface SeverityLimits {
  maxCritical: number;
  maxHigh: number;
  maxMedium: number;
}

export interface ScanSettings {
  coverageMinLine: number;
  sastPrepareTimeoutMinutes: number;
  sastPollTimeoutMinutes: number;
  sastPollIntervalSeconds: number;
  dastPollTimeoutMinutes: number;
  dastPollIntervalSeconds: number;
  dastReportTimeoutMinutes: number;
  dastReportIntervalSeconds: number;
  sonarWaitForQualityGate: boolean;
  sonarQualityGateTimeoutMinutes: number;
}

export interface ReleaseGateSettings {
  scanners: Scanner[];
  requireCoverage: boolean;
  stateFile: string;
}

export interface ServiceDefaults {
  buildTool: BuildTool;
  deployTarget: DeployTarget;
  sourceDir: string;
  testsMaxParallel: number;
}

export interface ServiceTemplateValues {
  agentLabels: string[];
  jenkinsJob: string | null;
  gradleTasks: string | null;
  gradleArtifact: string | null;
  gradleScanPattern: string | null;
  mavenTasks: string | null;
  mavenArtifact: string | null;
  mavenScanPattern: string | null;
  flutterScanPattern: string | null;
  deliveryTasks: string | null;
  nexusIqApplication: string | null;
  repositoryUrl: string | null;
  bitbucketCredentialsId: string | null;
  openShiftProject: string | null;
  imageRegistry: string | null;
  healthCheckUrl: string | null;
}

export interface ServiceTemplate extends ServiceTemplateValues {
  version: number | null;
  updatedAt: string | null;
}

export interface ServiceTemplateRequest extends ServiceTemplateValues {
  version: number | null;
}

export interface GlobalSettingsValues {
  platform: PlatformSettings;
  deployment: DeploymentDefaults;
  limits: Record<Scanner, SeverityLimits>;
  scans: ScanSettings;
  releaseGate: ReleaseGateSettings;
  serviceDefaults: ServiceDefaults;
  goldenFix: GlobalGoldenFixPolicy;
}

export interface GlobalSettings extends GlobalSettingsValues {
  version: number;
  updatedAt: string;
}

export interface GlobalSettingsRequest extends GlobalSettingsValues {
  version: number | null;
}

export interface PipelineRun {
  time: string;
  result: RunResult;
  branch: string | null;
  build: number | null;
  durationSeconds: number | null;
  commit: string | null;
  job: string | null;
  buildUrl: string | null;
  stagesTotal: number | null;
  passed: number | null;
  warned: number | null;
  failed: number | null;
  blocked: number | null;
  skipped: number | null;
}

export interface DailyActivity {
  date: string;
  runs: number;
  failures: number;
  deployments: number;
}

export interface DoraSummary {
  rangeDays: number;
  runs: number;
  deployments: number;
  deploymentsPerWeek: number | null;
  deploymentFrequencyLevel: DoraLevel | null;
  leadTimeMedianSeconds: number | null;
  leadTimeLevel: DoraLevel | null;
  changeFailureRatePercent: number | null;
  changeFailureRateLevel: DoraLevel | null;
  meanTimeToRestoreSeconds: number | null;
  timeToRestoreLevel: DoraLevel | null;
  restores: number;
  failingSince: string | null;
  averageDurationSeconds: number | null;
  daily: DailyActivity[];
}

export interface MonitoringStatus {
  influxConfigured: boolean;
  influxReachable: boolean;
  influxError: string | null;
}

export interface ProductHealth {
  productId: number;
  code: string;
  name: string;
  ownerTeam: string | null;
  departmentId: number | null;
  serviceCount: number;
  pipelineCount: number;
  overall: RunResult;
  statusCounts: Partial<Record<RunResult, number>>;
  lastRunAt: string | null;
}

export interface MonitoringOverview {
  products: ProductHealth[];
  metricsError: string | null;
}

export interface PortfolioActivity {
  pipelines: number;
  dora: DoraSummary;
  metricsError: string | null;
}

export interface PipelineHealth {
  pipeline: Pipeline;
  status: RunResult;
  lastRun: PipelineRun | null;
}

export interface DepartmentPipelines {
  pipelines: PipelineHealth[];
  metricsError: string | null;
}

export interface ProductMonitoring {
  productId: number;
  code: string;
  name: string;
  description: string | null;
  ownerTeam: string | null;
  overall: RunResult;
  pipelines: PipelineHealth[];
  metricsError: string | null;
}

export interface PipelineMonitoring {
  pipeline: Pipeline;
  status: RunResult;
  lastRun: PipelineRun | null;
  dora: DoraSummary;
  recentRuns: PipelineRun[];
  grafana: GrafanaDashboard[];
  metricsError: string | null;
}

export interface GrafanaDashboard {
  name: string;
  dashboardUrl: string;
}

export const PIPELINE_TYPES: { value: PipelineType; label: string; description: string }[] = [
  {
    value: 'FULL',
    label: 'Full',
    description: 'Build, scans, tests, deployment and release',
  },
  {
    value: 'SECURITY',
    label: 'Security',
    description: 'Build and security scans, optionally starts the extended pipeline',
  },
  {
    value: 'EXTENDED',
    label: 'Extended',
    description: 'Deployment and tests started by the security pipeline',
  },
  { value: 'SAST', label: 'SAST scanning', description: 'AppScan static scan of the sources only' },
  {
    value: 'NEXUS_IQ',
    label: 'Nexus IQ GoldenFix',
    description:
      "Nexus IQ dependency scan; GoldenFix opens a pull request with safe versions in the service's Bitbucket repository",
  },
];

export function pipelineTypeLabel(type: PipelineType): string {
  return PIPELINE_TYPES.find((option) => option.value === type)?.label ?? type;
}

export function pipelineTypeSlug(type: PipelineType): string {
  return type === 'NEXUS_IQ' ? 'nexusiq' : type.toLowerCase();
}

export function pipelineTypeName(type: PipelineType): string {
  return type === 'NEXUS_IQ' ? pipelineTypeLabel(type) : type.toLowerCase();
}

export const DEFAULT_JENKINS_LIBRARY = 'DevSecOpsJenkinsLibrary';
export const RELEASE_GATE_FILE = 'release-gate.json';
