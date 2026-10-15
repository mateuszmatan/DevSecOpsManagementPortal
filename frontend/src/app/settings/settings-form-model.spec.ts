import { GlobalSettingsValues } from '../core/models';
import { globalSettings } from '../testing/fixtures';
import { applyFieldProblems, controlAt } from '@common/shared/form-controls';
import {
  createSettingsForm,
  firstInvalidSection,
  patchSettings,
  toSettingsRequest,
} from './settings-form-model';

function values(): GlobalSettingsValues {
  const { version, updatedAt, ...rest } = globalSettings();
  expect(version).toBe(4);
  expect(updatedAt).toBeTruthy();
  return rest;
}

describe('createSettingsForm', () => {
  it('starts empty forms with the library names and every scanner in the release gate', () => {
    const value = createSettingsForm().getRawValue();

    expect(value.platform.jenkinsLibrary).toBe('DevSecOpsJenkinsLibrary');
    expect(value.releaseGate).toEqual({
      scanners: ['SAST', 'SCA', 'NEXUS_IQ', 'DAST'],
      requireCoverage: true,
      stateFile: 'release-gate.json',
    });
    expect(value.serviceDefaults).toEqual({
      buildTool: 'GRADLE',
      deployTarget: 'VM',
      sourceDir: '.',
      testsMaxParallel: null,
    });
  });

  it('requires the tool servers, the deployment defaults and every limit', () => {
    const form = createSettingsForm();
    const c = form.controls;

    expect(c.platform.controls.asocUrl.hasError('required')).toBe(true);
    expect(c.platform.controls.sonarServerUrl.hasError('required')).toBe(true);
    expect(c.platform.controls.jenkinsUrl.valid).toBe(true);
    expect(c.deployment.controls.rdHost.hasError('required')).toBe(true);
    expect(c.limits.controls.NEXUS_IQ.controls.maxHigh.hasError('required')).toBe(true);
    expect(c.scans.controls.coverageMinLine.hasError('required')).toBe(true);
    expect(c.goldenFix.controls.minThreatLevel.hasError('required')).toBe(true);
  });

  it('checks addresses, host names and numbers', () => {
    const form = createSettingsForm(globalSettings());
    const { platform, deployment, limits, scans } = form.controls;

    platform.controls.jenkinsUrl.setValue('jenkins.bbh.com');
    deployment.controls.qcHost.setValue('qc host');
    limits.controls.SAST.controls.maxCritical.setValue(-1);
    scans.controls.coverageMinLine.setValue(100.5);
    scans.controls.sastPollIntervalSeconds.setValue(3601);
    platform.controls.nexusSnapshotRepositoryUrl.setValue('https://nexus/snapshots&id');
    platform.controls.nexusSnapshotRepositoryId.setValue('snapshots;id');

    expect(platform.controls.jenkinsUrl.hasError('pattern')).toBe(true);
    expect(platform.controls.nexusSnapshotRepositoryUrl.hasError('pattern')).toBe(true);
    expect(platform.controls.nexusSnapshotRepositoryId.hasError('pattern')).toBe(true);
    expect(deployment.controls.qcHost.hasError('pattern')).toBe(true);
    expect(limits.controls.SAST.controls.maxCritical.hasError('min')).toBe(true);
    expect(scans.controls.coverageMinLine.hasError('integer')).toBe(true);
    expect(scans.controls.sastPollIntervalSeconds.hasError('max')).toBe(true);
  });

  it('needs a coverage minimum of at least 1 and at least one scanner in the release gate', () => {
    const form = createSettingsForm(globalSettings());
    const { scans, releaseGate } = form.controls;

    scans.controls.coverageMinLine.setValue(0);
    releaseGate.controls.scanners.setValue([]);

    expect(scans.controls.coverageMinLine.errors).toEqual({ min: { min: 1, actual: 0 } });
    expect(releaseGate.controls.scanners.errors).toEqual({ rule: 'Select at least one scanner' });

    scans.controls.coverageMinLine.setValue(1);
    releaseGate.controls.scanners.setValue(['SCA']);
    expect(scans.controls.coverageMinLine.valid && releaseGate.controls.scanners.valid).toBe(true);
  });
});

describe('patchSettings', () => {
  it('fills the form with stored settings and leaves it unchanged', () => {
    const form = createSettingsForm();
    form.markAsDirty();

    patchSettings(form, globalSettings({ scans: { ...values().scans, coverageMinLine: 75 } }));

    expect(form.dirty).toBe(false);
    expect(form.valid).toBe(true);
    expect(form.controls.scans.controls.coverageMinLine.value).toBe(75);
    expect(form.controls.goldenFix.controls.goldenVersionTypes.value).toBe(
      'recommended-non-breaking-with-dependencies\nrecommended-non-breaking',
    );
  });
});

describe('toSettingsRequest', () => {
  it('round-trips the stored settings with the version they were read at', () => {
    expect(toSettingsRequest(createSettingsForm(globalSettings()), 4)).toEqual({
      version: 4,
      ...values(),
    });
  });

  it('always sends release-gate.json as the release gate file, the only name the library reads', () => {
    const settings = globalSettings();
    const form = createSettingsForm({
      ...settings,
      releaseGate: { ...settings.releaseGate, stateFile: 'custom-gate.json' },
    });

    expect(form.controls.releaseGate.controls.stateFile.value).toBe('release-gate.json');
    expect(toSettingsRequest(form, 4).releaseGate.stateFile).toBe('release-gate.json');
  });

  it('trims values, sends blank optional ones as null and keeps the scanners in policy order', () => {
    const form = createSettingsForm(globalSettings());
    form.patchValue({
      platform: {
        jenkinsUrl: '  ',
        jenkinsLibrary: ' BBHLibrary ',
        proxyHost: '',
        proxyPort: null,
        proxyUser: ' ',
        iosBuildAgent: ' mac-mini ',
      },
      deployment: { sshUser: ' taadmin ' },
      releaseGate: { scanners: ['DAST', 'SAST'], stateFile: ' gate.json ' },
      serviceDefaults: { sourceDir: ' ' },
    });

    const request = toSettingsRequest(form, null);

    expect(request.version).toBeNull();
    expect(request.platform).toMatchObject({
      jenkinsUrl: null,
      jenkinsLibrary: 'BBHLibrary',
      proxyHost: null,
      proxyPort: null,
      proxyUser: null,
      iosBuildAgent: 'mac-mini',
    });
    expect(request.deployment.sshUser).toBe('taadmin');
    expect(request.releaseGate).toEqual({
      scanners: ['SAST', 'DAST'],
      requireCoverage: true,
      stateFile: 'release-gate.json',
    });
    expect(request.serviceDefaults.sourceDir).toBe('.');
  });

  it('sends the limits of every scanner keyed by scanner', () => {
    const form = createSettingsForm(globalSettings());
    form.controls.limits.controls.DAST.patchValue({ maxHigh: 3 });

    expect(toSettingsRequest(form, 4).limits).toEqual({
      SAST: { maxCritical: 0, maxHigh: 0, maxMedium: 0 },
      SCA: { maxCritical: 0, maxHigh: 0, maxMedium: 0 },
      NEXUS_IQ: { maxCritical: 0, maxHigh: 2, maxMedium: 10 },
      DAST: { maxCritical: 0, maxHigh: 3, maxMedium: 0 },
    });
  });
});

describe('sections of the settings page', () => {
  it('finds the first section holding an invalid value', () => {
    const form = createSettingsForm(globalSettings());
    expect(firstInvalidSection(form)).toBeNull();

    form.controls.goldenFix.controls.commitAuthorEmail.setValue('not an address');
    form.controls.scans.controls.dastPollTimeoutMinutes.setValue(0);

    expect(firstInvalidSection(form)).toBe('scans');
  });

  it('shows the problems the API reports on their controls', () => {
    const form = createSettingsForm(globalSettings());
    const unmatched = applyFieldProblems(form, [
      { field: 'limits[NEXUS_IQ].maxHigh', message: 'must be less than or equal to 100000' },
      { field: 'platform.proxyPort', message: 'is required with a proxy host' },
      { field: 'releaseGate.scanners[1]', message: 'must not be null' },
      { field: 'version', message: 'is stale' },
    ]);

    expect(form.controls.limits.controls.NEXUS_IQ.controls.maxHigh.errors).toEqual({
      server: 'must be less than or equal to 100000',
    });
    expect(form.controls.platform.controls.proxyPort.hasError('server')).toBe(true);
    expect(controlAt(form, 'releaseGate.scanners[1]')).toBe(
      form.controls.releaseGate.controls.scanners,
    );
    expect(form.controls.releaseGate.controls.scanners.hasError('server')).toBe(true);
    expect(unmatched).toEqual([{ field: 'version', message: 'is stale' }]);
    expect(firstInvalidSection(form)).toBe('platform');
  });
});
