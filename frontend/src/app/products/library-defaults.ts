import { GlobalSettings, REGIONS } from '../core/models';
import { joinLines } from '@common/shared/form-controls';
import type { ServiceForm } from './product-form-model';

type ServiceValue = ReturnType<ServiceForm['getRawValue']>;

function serviceDefaults({ platform, deployment, serviceDefaults }: GlobalSettings) {
  const ssh = REGIONS.flatMap((region) =>
    Object.entries({
      host: region === 'RD' ? deployment.rdHost : deployment.qcHost,
      user: deployment.sshUser,
      deployScript: deployment.deployScript,
      versionFile: deployment.versionFile,
    }).map(([key, value]) => [`sshTargets.${region}.${key}`, value]),
  );
  return {
    'sonar.serverUrl': platform.sonarServerUrl,
    'sonar.installationName': platform.sonarInstallationName,
    'nexusIq.serverUrl': platform.nexusIqServerUrl,
    'nexusIq.credentialsId': platform.nexusIqCredentialsId,
    'metrics.influxUrl': platform.influxWriteUrl,
    'metrics.influxCredentialsId': platform.influxCredentialsId,
    'urbanCode.siteName': deployment.urbanCodeSiteName,
    'urbanCode.deployProcess': deployment.urbanCodeDeployProcess,
    ...Object.fromEntries(ssh),
    'tests.maxParallel': serviceDefaults.testsMaxParallel,
    'nodeBuild.installationName': serviceDefaults.nodeInstallationName,
    'nodeBuild.registry': serviceDefaults.npmRegistry,
    'nodeBuild.npmrcCredentialsId': serviceDefaults.npmrcCredentialsId,
  };
}

function goldenFixDefaults({ goldenFix: { enabled, ...policy } }: GlobalSettings) {
  const values = {
    ...policy,
    goldenVersionTypes: joinLines(policy.goldenVersionTypes),
    excludeDirs: joinLines(policy.excludeDirs),
  };
  return Object.fromEntries(
    Object.entries(values).map(([key, value]) => [`goldenFix.${key}`, value]),
  );
}

const prefilled = new WeakMap<ServiceForm, Map<string, unknown>>();

const empty = (value: unknown) =>
  value === null ||
  value === undefined ||
  (typeof value === 'string' && !value.trim()) ||
  (Array.isArray(value) && !value.length);

const same = (value: unknown, other: unknown) =>
  JSON.stringify(typeof value === 'string' ? value.trim() : value) === JSON.stringify(other);

function prefill(form: ServiceForm, values: Record<string, unknown>): void {
  const record = prefilled.get(form) ?? new Map<string, unknown>();
  prefilled.set(form, record);
  for (const [path, value] of Object.entries(values)) {
    const control = form.get(path);
    if (control && !empty(value) && empty(control.value)) {
      control.setValue(value);
      record.set(path, value);
    }
  }
}

/** Fills the empty controls of a new service with the Library defaults and remembers them. */
export function applyLibraryDefaults(form: ServiceForm, settings: GlobalSettings | null): void {
  if (!settings) {
    return;
  }
  prefill(form, serviceDefaults(settings));
  const inherit = form.controls.goldenFix.controls.inherit;
  const override = () => !inherit.value && prefill(form, goldenFixDefaults(settings));
  inherit.valueChanges.subscribe(override);
  override();
}

/** The form value with every prefilled default the user left unchanged taken out again. */
export function withoutUntouchedDefaults(form: ServiceForm): ServiceValue {
  const value = form.getRawValue();
  prefilled.get(form)?.forEach((initial, path) => {
    const keys = path.split('.');
    const last = keys.pop()!;
    const parent = keys.reduce<Record<string, unknown>>(
      (node, key) => node[key] as Record<string, unknown>,
      value,
    );
    const current = parent[last];
    if (same(current, initial)) {
      parent[last] = typeof current === 'string' ? '' : Array.isArray(current) ? [] : null;
    }
  });
  return value;
}
