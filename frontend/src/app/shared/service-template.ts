import { ValidatorFn } from '@angular/forms';
import { BuildTool, PipelineType, ServiceTemplateValues, pipelineTypeSlug } from '../core/models';

export const SERVICE_PLACEHOLDERS = ['CODE', 'code', 'service'] as const;
export const JOB_PLACEHOLDERS = [...SERVICE_PLACEHOLDERS, 'type'] as const;

const PLACEHOLDER = /\{([^{}]*)}/g;

const braced = (names: readonly string[]) => names.map((name) => `{${name}}`).join(', ');

export function fillTemplate(
  pattern: string | null | undefined,
  productCode: string,
  serviceName: string,
  type?: PipelineType,
): string {
  const values: Record<string, string | undefined> = {
    CODE: productCode,
    code: productCode.toLowerCase(),
    service: serviceName,
    type: type && pipelineTypeSlug(type),
  };
  return (pattern ?? '').replace(PLACEHOLDER, (placeholder, name: string) =>
    Object.hasOwn(values, name) && values[name] !== undefined ? values[name] : placeholder,
  );
}

export function knownPlaceholders(known: readonly string[]): ValidatorFn {
  return (control) => {
    const unknown = [
      ...new Set(
        [...String(control.value ?? '').matchAll(PLACEHOLDER)]
          .map((match) => match[1])
          .filter((name) => !known.includes(name)),
      ),
    ];
    return unknown.length
      ? { rule: `Unknown placeholder ${braced(unknown)}: use ${braced(known)}` }
      : null;
  };
}

export interface NamedDefaults {
  openShiftProject: string;
  nexusIqApplication: string;
  repositoryUrl: string;
}

export function namedDefaults(
  defaults: { template: ServiceTemplateValues | null; productCode: string },
  serviceName: string,
): NamedDefaults {
  const { template, productCode } = defaults;
  const filled = (pattern: string | null | undefined) =>
    productCode && serviceName ? fillTemplate(pattern, productCode, serviceName) : '';
  return {
    openShiftProject: filled(template?.openShiftProject).toLowerCase(),
    nexusIqApplication: filled(template?.nexusIqApplication),
    repositoryUrl: filled(template?.repositoryUrl),
  };
}

/** The image repository of a service: the template's registry, the OpenShift project and the service name. */
export function imageRepository(
  project: string,
  service: string,
  template: ServiceTemplateValues | null,
): string {
  const registry = template?.imageRegistry;
  return `${registry ? `${registry}/` : ''}${project}/${service.toLowerCase()}`;
}

export interface BuildDefaults {
  tasks: string;
  artifact: string;
  scanPattern: string;
}

export function buildDefaults(
  template: ServiceTemplateValues | null,
  tool: BuildTool,
): BuildDefaults {
  switch (tool) {
    case 'MAVEN':
      return {
        tasks: template?.mavenTasks ?? '',
        artifact: template?.mavenArtifact ?? '',
        scanPattern: template?.mavenScanPattern ?? '',
      };
    case 'FLUTTER':
      return { tasks: '', artifact: '', scanPattern: template?.flutterScanPattern ?? '' };
    case 'LIQUIBASE':
      return { tasks: 'updateSQL', artifact: '', scanPattern: '' };
    default:
      return {
        tasks: template?.gradleTasks ?? '',
        artifact: template?.gradleArtifact ?? '',
        scanPattern: template?.gradleScanPattern ?? '',
      };
  }
}
