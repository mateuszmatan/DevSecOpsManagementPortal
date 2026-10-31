import { FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import {
  BuildTool,
  DEFAULT_JENKINS_LIBRARY,
  DatabaseDefaults,
  DeployTarget,
  GlobalSettingsRequest,
  GlobalSettingsValues,
  RELEASE_GATE_FILE,
  SCANNERS,
  Scanner,
  SeverityLimits,
} from '../core/models';
import {
  createGlobalGoldenFixForm,
  inUse,
  packageRepositories,
  toGlobalGoldenFixPolicy,
} from '../products/product-form-model';
import {
  ABSOLUTE,
  ABSOLUTE_MESSAGE,
  ARCHIVE,
  ARCHIVE_MESSAGE,
  JAR,
  JAR_MESSAGE,
  SHA256,
  SHA256_MESSAGE,
  TARGET,
  TARGET_MESSAGE,
  TOOL_URL,
  TOOL_URL_MESSAGE,
  ZIP,
  ZIP_MESSAGE,
  matching,
} from '../shared/database-rules';
import {
  HOST_NAME,
  INT_MAX,
  SHELL_SAFE_URL,
  eachItem,
  filled,
  fitsColumn,
  flag,
  integer,
  joinLines,
  lines,
  max,
  maxItems,
  optional,
  requireWhile,
  requiredRule,
  sent,
  shellSafe,
  text,
  url,
} from '@common/shared/form-controls';

const host = (value: string | null | undefined, ...validators: ValidatorFn[]) =>
  text(value, ...validators, Validators.pattern(HOST_NAME), max(255));

const absolute = (value: string | null | undefined, ...validators: ValidatorFn[]) =>
  text(value, matching(ABSOLUTE, ABSOLUTE_MESSAGE), ...validators);
const download = (value: string | null | undefined, file: RegExp, message: string) =>
  text(value, max(1000), matching(TOOL_URL, TOOL_URL_MESSAGE), matching(file, message));

/** The tools, allowed databases and package repositories of every SQL and PL/SQL service. */
function createDatabaseDefaultsForm(d?: DatabaseDefaults | null) {
  const form = new FormGroup({
    liquibaseHome: absolute(d?.liquibaseHome),
    liquibaseUrl: download(d?.liquibaseUrl, ARCHIVE, ARCHIVE_MESSAGE),
    liquibaseSha256: text(d?.liquibaseSha256, matching(SHA256, SHA256_MESSAGE)),
    liquibaseJavaVersion: integer(d?.liquibaseJavaVersion, 17, 25),
    oracleJdbcPath: absolute(d?.oracleJdbcPath, matching(JAR, JAR_MESSAGE)),
    oracleJdbcUrl: download(d?.oracleJdbcUrl, JAR, JAR_MESSAGE),
    oracleJdbcSha256: text(d?.oracleJdbcSha256, matching(SHA256, SHA256_MESSAGE)),
    utplsqlHome: absolute(d?.utplsqlHome),
    utplsqlUrl: download(d?.utplsqlUrl, ZIP, ZIP_MESSAGE),
    utplsqlSha256: text(d?.utplsqlSha256, matching(SHA256, SHA256_MESSAGE)),
    allowedTargets: text(
      joinLines(d?.allowedTargets),
      maxItems(lines, 200),
      eachItem(lines, TARGET, TARGET_MESSAGE),
      fitsColumn(lines, '\n', 4000),
    ),
    ...packageRepositories(d),
  });
  const c = form.controls;
  (['liquibase', 'oracleJdbc', 'utplsql'] as const).forEach((tool) => {
    const url = c[`${tool}Url`];
    requireWhile(c[`${tool}Sha256`], () => !!optional(url.value), url);
  });
  return form;
}

function createLimitsForm(limits?: SeverityLimits) {
  const count = (value: number | undefined) => integer(value, 0, 100_000, filled);
  return new FormGroup({
    maxCritical: count(limits?.maxCritical),
    maxHigh: count(limits?.maxHigh),
    maxMedium: count(limits?.maxMedium),
  });
}

export function createSettingsForm(settings?: GlobalSettingsValues | null) {
  const p = settings?.platform;
  const d = settings?.deployment;
  const s = settings?.scans;
  const minutes = (value: number | undefined) => integer(value, 1, 1440, filled);
  const seconds = (value: number | undefined) => integer(value, 1, 3600, filled);
  return new FormGroup({
    platform: new FormGroup({
      jenkinsUrl: url(p?.jenkinsUrl, 500),
      jenkinsLibrary: text(p?.jenkinsLibrary ?? DEFAULT_JENKINS_LIBRARY, filled, max(200)),
      asocUrl: url(p?.asocUrl, 500, filled),
      appScanClientLinuxUrl: url(p?.appScanClientLinuxUrl, 1000, filled),
      appScanClientWindowsUrl: url(p?.appScanClientWindowsUrl, 1000, filled),
      proxyHost: host(p?.proxyHost, filled),
      proxyPort: integer(p?.proxyPort, 1, 65535, filled),
      proxyUser: text(p?.proxyUser, filled, max(100)),
      oisHost: host(p?.oisHost),
      sonarServerUrl: url(p?.sonarServerUrl, 500, filled),
      sonarInstallationName: text(p?.sonarInstallationName, filled, max(200)),
      nexusIqServerUrl: url(p?.nexusIqServerUrl, 500, filled),
      nexusIqCredentialsId: text(p?.nexusIqCredentialsId, filled, max(200)),
      nexusSnapshotRepositoryUrl: text(
        p?.nexusSnapshotRepositoryUrl,
        Validators.pattern(SHELL_SAFE_URL),
        max(1000),
      ),
      nexusSnapshotRepositoryId: shellSafe(p?.nexusSnapshotRepositoryId, 200),
      influxWriteUrl: url(p?.influxWriteUrl, 1000),
      influxCredentialsId: text(p?.influxCredentialsId, max(200)),
      iosBuildAgent: text(p?.iosBuildAgent, max(255)),
      influxUrl: url(p?.influxUrl, 500),
      influxOrg: text(p?.influxOrg, max(200)),
      influxBucket: text(p?.influxBucket, max(200)),
      influxToken: text(p?.influxToken, max(500)),
      grafanaName: text(p?.grafanaName, max(100)),
      grafanaDashboardUrl: url(p?.grafanaDashboardUrl, 1000),
      grafanaSecurityDashboardUrl: url(p?.grafanaSecurityDashboardUrl, 1000),
      grafana2Name: text(p?.grafana2Name, max(100)),
      grafana2DashboardUrl: url(p?.grafana2DashboardUrl, 1000),
      grafana2SecurityDashboardUrl: url(p?.grafana2SecurityDashboardUrl, 1000),
    }),
    deployment: new FormGroup({
      urbanCodeSiteName: text(d?.urbanCodeSiteName, filled, max(200)),
      urbanCodeDeployProcess: text(d?.urbanCodeDeployProcess, filled, max(200)),
      rdHost: host(d?.rdHost, filled),
      qcHost: host(d?.qcHost, filled),
      sshUser: shellSafe(d?.sshUser, 100, filled),
      deployScript: shellSafe(d?.deployScript, 500, filled),
      versionFile: shellSafe(d?.versionFile, 500, filled),
    }),
    limits: new FormGroup({
      SAST: createLimitsForm(settings?.limits.SAST),
      SCA: createLimitsForm(settings?.limits.SCA),
      NEXUS_IQ: createLimitsForm(settings?.limits.NEXUS_IQ),
      DAST: createLimitsForm(settings?.limits.DAST),
    }),
    scans: new FormGroup({
      coverageMinLine: integer(s?.coverageMinLine, 1, 100, filled),
      sastPrepareTimeoutMinutes: minutes(s?.sastPrepareTimeoutMinutes),
      sastPollTimeoutMinutes: minutes(s?.sastPollTimeoutMinutes),
      sastPollIntervalSeconds: seconds(s?.sastPollIntervalSeconds),
      dastPollTimeoutMinutes: minutes(s?.dastPollTimeoutMinutes),
      dastPollIntervalSeconds: seconds(s?.dastPollIntervalSeconds),
      dastReportTimeoutMinutes: minutes(s?.dastReportTimeoutMinutes),
      dastReportIntervalSeconds: seconds(s?.dastReportIntervalSeconds),
      sonarWaitForQualityGate: flag(s?.sonarWaitForQualityGate, true),
      sonarQualityGateTimeoutMinutes: minutes(s?.sonarQualityGateTimeoutMinutes),
      parallelStaticAnalysis: flag(s?.parallelStaticAnalysis, true),
    }),
    releaseGate: new FormGroup({
      scanners: new FormControl<Scanner[]>(settings?.releaseGate.scanners ?? [...SCANNERS], {
        nonNullable: true,
        validators: requiredRule('Select at least one scanner'),
      }),
      requireCoverage: flag(settings?.releaseGate.requireCoverage, true),
      stateFile: new FormControl(RELEASE_GATE_FILE, { nonNullable: true }),
    }),
    serviceDefaults: new FormGroup({
      buildTool: new FormControl<BuildTool>(settings?.serviceDefaults.buildTool ?? 'GRADLE', {
        nonNullable: true,
      }),
      deployTarget: new FormControl<DeployTarget>(settings?.serviceDefaults.deployTarget ?? 'VM', {
        nonNullable: true,
      }),
      sourceDir: text(settings?.serviceDefaults.sourceDir ?? '.', max(500)),
      testsMaxParallel: integer(settings?.serviceDefaults.testsMaxParallel, 1, INT_MAX, filled),
      nodeInstallationName: text(settings?.serviceDefaults.nodeInstallationName, max(200)),
      npmRegistry: url(settings?.serviceDefaults.npmRegistry, 500),
      npmrcCredentialsId: text(settings?.serviceDefaults.npmrcCredentialsId, max(200)),
    }),
    database: createDatabaseDefaultsForm(settings?.database),
    goldenFix: createGlobalGoldenFixForm(settings?.goldenFix),
  });
}

type SettingsForm = ReturnType<typeof createSettingsForm>;
export type SettingsSectionId = keyof SettingsForm['controls'];

export function patchSettings(form: SettingsForm, settings: GlobalSettingsValues): void {
  form.reset(createSettingsForm(settings).getRawValue());
}

export function toSettingsRequest(
  form: SettingsForm,
  version: number | null,
): GlobalSettingsRequest {
  const v = form.getRawValue();
  const limits = Object.fromEntries(
    SCANNERS.map((scanner) => [scanner, sent(v.limits[scanner])]),
  ) as Record<Scanner, SeverityLimits>;
  return {
    version,
    platform: sent(v.platform),
    deployment: sent(v.deployment),
    limits,
    scans: sent(v.scans),
    releaseGate: {
      ...sent(v.releaseGate),
      stateFile: RELEASE_GATE_FILE,
      scanners: SCANNERS.filter((scanner) => v.releaseGate.scanners.includes(scanner)),
    },
    serviceDefaults: {
      ...sent(v.serviceDefaults),
      sourceDir: optional(v.serviceDefaults.sourceDir) ?? '.',
    },
    goldenFix: toGlobalGoldenFixPolicy(form.controls.goldenFix),
    ...(inUse(v.database) && {
      database: { ...sent(v.database), allowedTargets: lines(v.database.allowedTargets) },
    }),
  };
}

export function firstInvalidSection(form: SettingsForm): SettingsSectionId | null {
  const ids = Object.keys(form.controls) as SettingsSectionId[];
  return ids.find((id) => form.controls[id].invalid) ?? null;
}
