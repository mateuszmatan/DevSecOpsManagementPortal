import { DEFAULT_JENKINS_LIBRARY, Pipeline, PipelineType } from '../core/models';

/** The pipeline types whose Nexus IQ stage runs GoldenFix. */
const GOLDEN_FIX_TYPES: readonly PipelineType[] = ['FULL', 'SECURITY', 'NEXUS_IQ'];

export function jenkinsfile(
  pipelines: readonly Pipeline[],
  library: string | null | undefined,
): string {
  const keys = pipelines.map(
    (pipeline) => `'${pipeline.activeKey?.value ?? '<issue a new key first>'}'`,
  );
  const name = library?.trim() || DEFAULT_JENKINS_LIBRARY;
  const call =
    keys.length === 1
      ? `pipelineKey: ${keys[0]}`
      : `pipelineKeys: [\n${keys.map((key) => `    ${key},`).join('\n')}\n]`;
  return `@Library('${name}') _\n\n${pipelines[0].entryPoint}(${call})\n`;
}

/**
 * Groups the items, in their order, whose active pipelines of one type share a Bitbucket
 * repository: one Jenkinsfile with all their keys runs them in one Jenkins job. Every other item
 * is a group of its own.
 */
export function byRepository<T>(
  items: readonly T[],
  repository: (item: T) => string | null | undefined,
  pipeline: (item: T) => Pipeline | null,
): T[][] {
  const groups = new Map<unknown, T[]>();
  for (const item of items) {
    const url = repository(item)
      ?.trim()
      .replace(/(\.git)?\/*$/, '')
      .toLowerCase();
    const run = pipeline(item);
    const key =
      url && run?.activeKey
        ? [url, run.type, run.securityPipelineJob, run.extendedPipelineJob].join(' ')
        : item;
    groups.set(key, [...(groups.get(key) ?? []), item]);
  }
  return [...groups.values()];
}

/** Why services that share a repository share one Jenkins job, or '' for pipelines without GoldenFix. */
export function sharedJobReason(type: PipelineType): string {
  return GOLDEN_FIX_TYPES.includes(type)
    ? ': one GoldenFix pull request then raises the shared catalog once'
    : '';
}
