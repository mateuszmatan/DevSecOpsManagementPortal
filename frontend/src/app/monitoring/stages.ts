import { PipelineRun } from '../core/models';

type StageCounts = Pick<
  PipelineRun,
  'passed' | 'notRequired' | 'warned' | 'failed' | 'blocked' | 'skipped'
>;

const STAGE_RESULTS: [keyof StageCounts, string][] = [
  ['passed', 'passed'],
  ['notRequired', 'not required'],
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

export const stagesPassed = (run: StageCounts) => (run.passed ?? 0) + (run.notRequired ?? 0);

export const stageSummary = (run: StageCounts) => summarize(run, STAGE_RESULTS);

export const stageProblems = (run: StageCounts) => summarize(run, STAGE_RESULTS.slice(2));
