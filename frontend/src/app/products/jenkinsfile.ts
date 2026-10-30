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
 * repository and their Jenkins agents: one Jenkinsfile with all their keys runs them in one
 * Jenkins job, on the agents of the first key. Every other item is a group of its own.
 */
export function byRepository<T>(
  items: readonly T[],
  repository: (item: T) => string | null | undefined,
  pipeline: (item: T) => Pipeline | null,
): T[][] {
  const groups = new Map<unknown, T[]>();
  for (const item of items) {
    const url = repositoryKey(repository(item));
    const run = pipeline(item);
    const key =
      url && run?.activeKey
        ? [url, run.type, run.securityPipelineJob, run.extendedPipelineJob, agents(run)].join(' ')
        : item;
    groups.set(key, [...(groups.get(key) ?? []), item]);
  }
  return [...groups.values()];
}

/** A repository URL as it names one repository, whatever its case, .git ending or trailing slash. */
export function repositoryKey(url: string | null | undefined): string {
  return (
    url
      ?.trim()
      .replace(/(\.git)?\/*$/, '')
      .toLowerCase() ?? ''
  );
}

/** The Jenkins agents of a pipeline, in a stable order. */
export function agents(pipeline: Pipeline): string {
  return [...pipeline.agentLabels].sort().join(',');
}

/** The pipelines that share one Jenkinsfile with the given one, in their order; only itself when none does. */
export function repositoryGroup(
  pipeline: Pipeline,
  all: readonly Pipeline[],
  repository: (pipeline: Pipeline) => string | null | undefined,
): Pipeline[] {
  const current = all.some((other) => other.id === pipeline.id)
    ? all.map((other) => (other.id === pipeline.id ? pipeline : other))
    : [...all, pipeline];
  return byRepository(current, repository, (other) => other).find((group) =>
    group.includes(pipeline),
  )!;
}

/** Where Pipeline Monitoring shows the runs of a Jenkins job that several services share. */
export function sharedRunsNote(firstService: string): string {
  return (
    `Pipeline Monitoring shows the runs of the shared job under ${firstService}, the service of ` +
    'the first key. To see them on the other services too, give them the same Project tag ' +
    '(Monitoring section) and the shared job as the Jenkins job of their pipelines.'
  );
}

/** Why services that share a repository share one Jenkins job, or '' for pipelines without GoldenFix. */
export function sharedJobReason(type: PipelineType): string {
  return GOLDEN_FIX_TYPES.includes(type)
    ? ': one GoldenFix pull request then raises the shared catalog once'
    : '';
}
