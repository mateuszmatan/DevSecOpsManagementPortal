import { PipelineRun } from '../core/models';

type StageCounts = Pick<PipelineRun, 'passed' | 'warned' | 'failed' | 'blocked' | 'skipped'>;

const STAGE_RESULTS: [keyof StageCounts, string][] = [
  ['passed', 'passed'],
  ['warned', 'with warnings'],
  ['failed', 'failed'],
  ['blocked', 'blocked'],
  ['skipped', 'skipped'],
];

function summarize(run: StageCounts, results: [keyof StageCounts, string][]): string {
  return results
    .filter(([key]) => run[key])
    .map(([key, label]) => `${run[key]} ${label}`)
    .join(', ');
}

export const stageSummary = (run: StageCounts) => summarize(run, STAGE_RESULTS);

export const stageProblems = (run: StageCounts) => summarize(run, STAGE_RESULTS.slice(1));
