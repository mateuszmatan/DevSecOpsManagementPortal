import { DEFAULT_JENKINS_LIBRARY, Pipeline } from '../core/models';

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
