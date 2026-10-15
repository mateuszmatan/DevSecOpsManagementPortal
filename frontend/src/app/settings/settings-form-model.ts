import { FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import {
  BuildTool,
  DEFAULT_JENKINS_LIBRARY,
  DeployTarget,
  GlobalSettingsRequest,
  GlobalSettingsValues,
  RELEASE_GATE_FILE,
  SCANNERS,
  Scanner,
  SeverityLimits,
} from '../core/models';
import { createGlobalGoldenFixForm, toGlobalGoldenFixPolicy } from '../products/product-form-model';
import {
  HOST_NAME,
  INT_MAX,
  SHELL_SAFE_URL,
  filled,
  flag,
  integer,
  max,
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
  const form = new FormGroup({
    platform: new FormGroup({
      jenkinsUrl: url(p?.jenkinsUrl, 500),
      jenkinsLibrary: text(p?.jenkinsLibrary ?? DEFAULT_JENKINS_LIBRARY, filled, max(200)),
      asocUrl: url(p?.asocUrl, 500, filled),
      appScanClientLinuxUrl: url(p?.appScanClientLinuxUrl, 1000, filled),
      appScanClientWindowsUrl: url(p?.appScanClientWindowsUrl, 1000, filled),
      proxyHost: host(p?.proxyHost),
      proxyPort: integer(p?.proxyPort, 1, 65535),
      proxyUser: text(p?.proxyUser, max(100)),
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
    }),
    goldenFix: createGlobalGoldenFixForm(settings?.goldenFix),
  });
  const { proxyHost, proxyPort } = form.controls.platform.controls;
  requireWhile(proxyPort, () => !!optional(proxyHost.value), proxyHost);
  requireWhile(proxyHost, () => proxyPort.value !== null, proxyPort);
  return form;
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
  };
}

export function firstInvalidSection(form: SettingsForm): SettingsSectionId | null {
  const ids = Object.keys(form.controls) as SettingsSectionId[];
  return ids.find((id) => form.controls[id].invalid) ?? null;
}
