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

export const KEYLESS_JENKINSFILE_HELP =
  'For a repository that keeps the settings of its services in devsecops_config.yaml instead of ' +
  'fetching them with a pipeline key. Commit it at the root of the repository with ' +
  'devsecops_config.yaml, which "Settings sent to Jenkins (devsecops_config.yaml)" in the More menu ' +
  'of the product downloads. It names the services of this repository, so the other services of ' +
  'that file are not built. A change in the portal reaches the pipeline only when the file is ' +
  'downloaded and committed again. The Jenkins administrators can require a pipeline key; this ' +
  'Jenkinsfile then stops at its start.';
