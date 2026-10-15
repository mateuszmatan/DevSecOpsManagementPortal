import { Pipeline, PipelineType, pipelineTypeLabel } from '../core/models';

export function typeName(type: PipelineType): string {
  const label = pipelineTypeLabel(type);
  return /[A-Z]/.test(label.slice(1)) ? label : label.toLowerCase();
}

export function pipelineName(pipeline: Pick<Pipeline, 'serviceName' | 'type'>): string {
  return `${pipeline.serviceName} · ${pipelineTypeLabel(pipeline.type)}`;
}

export const KEY_MEANING =
  "the secret code the service's Jenkins job uses to fetch its settings from this portal";

export const JENKINSFILE_HELP =
  "The file to put in the service's repository so Jenkins runs this pipeline. It holds only the " +
  'pipeline key: Jenkins fetches every other setting from this portal.';
