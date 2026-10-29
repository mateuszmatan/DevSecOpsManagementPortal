import { globalSettings, service } from '../testing/fixtures';
import { applyLibraryDefaults } from './library-defaults';
import { createServiceForm, toServiceRequest } from './product-form-model';

describe('Library defaults of a new service', () => {
  it('fills the empty settings with the Library defaults and sends the untouched ones as null', () => {
    const form = createServiceForm();
    applyLibraryDefaults(form, globalSettings());
    const c = form.controls;

    expect(c.sonar.controls.serverUrl.value).toBe('https://tools.bbh.com/sonar');
    expect(c.nexusIq.controls.credentialsId.value).toBe('nexusiqP');
    expect(c.urbanCode.controls.siteName.value).toBe('deploy.bbh.com');
    expect(c.sshTargets.controls.QC.controls.host.value).toBe('qcltaapps1.testbbh.com');
    expect(c.tests.controls.maxParallel.value).toBe(20);

    c.sonar.controls.installationName.setValue('SonarQube EU');
    c.metrics.controls.influxUrl.setValue(' http://qcwsecopsmon1.testbbh.com:8086/api/v2/write ');
    const request = toServiceRequest(form);

    expect(request.sonar.serverUrl).toBeNull();
    expect(request.sonar.installationName).toBe('SonarQube EU');
    expect(request.metrics.influxUrl).toBeNull();
    expect(request.urbanCode.siteName).toBeNull();
    expect(request.tests.maxParallel).toBeNull();
    expect(request.sshTargets).toEqual({});
  });

  it('keeps what the service already holds and records nothing for it', () => {
    const stored = service({
      sonar: { ...service().sonar, serverUrl: 'https://tools.bbh.com/sonar' },
    });
    const form = createServiceForm(stored);
    applyLibraryDefaults(form, globalSettings());

    expect(toServiceRequest(form).sonar.serverUrl).toBe('https://tools.bbh.com/sonar');
  });

  it('fills nothing without the global settings', () => {
    const form = createServiceForm();
    applyLibraryDefaults(form, null);

    expect(form.controls.sonar.controls.serverUrl.value).toBe('');
  });

  it('starts a GoldenFix override with the global policy and sends it as an override once changed', () => {
    const form = createServiceForm();
    applyLibraryDefaults(form, globalSettings());
    const goldenFix = form.controls.goldenFix.controls;

    goldenFix.inherit.setValue(false);

    expect(goldenFix.minThreatLevel.value).toBe(globalSettings().goldenFix.minThreatLevel);
    expect(toServiceRequest(form).goldenFix.minThreatLevel).toBeNull();

    goldenFix.minThreatLevel.setValue(9);

    expect(toServiceRequest(form).goldenFix.minThreatLevel).toBe(9);
  });
});
