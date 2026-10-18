import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { PipelineRun } from '../core/models';
import { BuildLink } from '../shared/build-link';
import { RUN_LOOK, StatusChip } from '../shared/status-chip';
import { formatDuration } from '@common/shared/formatting';
import { GRID, GridColumn } from '@common/ui/grid';
import { stagesPassed } from './stages';

export type RunDetail = 'commit' | 'stages';

const DETAILS = new Set<string>(['commit', 'stages']);

const COLUMNS: GridColumn<PipelineRun>[] = [
  { key: 'time', header: 'Finished', value: (run) => run.time, width: 130 },
  { key: 'result', header: 'Result', value: (run) => RUN_LOOK[run.result]?.label, minWidth: 170 },
  { key: 'build', header: 'Build', value: (run) => run.build ?? '', width: 90 },
  {
    key: 'branch',
    header: 'Branch',
    value: (run) => run.branch ?? '–',
    cellClass: 'mono',
    wrap: true,
    minWidth: 140,
  },
  {
    key: 'commit',
    header: 'Commit',
    value: (run) => (run.commit ? run.commit.slice(0, 10) : '–'),
    cellClass: 'mono',
    width: 130,
  },
  {
    key: 'duration',
    header: 'Duration',
    value: (run) => formatDuration(run.durationSeconds),
    sortValue: (run) => run.durationSeconds ?? -1,
    width: 110,
  },
  {
    key: 'stages',
    header: 'Stages passed',
    value: (run) => (run.stagesTotal ? `${stagesPassed(run)} of ${run.stagesTotal}` : '–'),
    width: 140,
  },
];

@Component({
  selector: 'dso-run-grid',
  imports: [DatePipe, GRID, BuildLink, StatusChip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dso-grid [label]="label()" [rows]="runs()" [columns]="columns()" [rowId]="runId">
      <ng-template dsoCell="time" let-run>
        <span [title]="run.time | date: 'd MMM y, HH:mm:ss'">{{
          run.time | date: 'd MMM, HH:mm'
        }}</span>
      </ng-template>
      <ng-template dsoCell="result" let-run>
        <dso-status-chip [status]="run.result" />
      </ng-template>
      <ng-template dsoCell="build" let-run><dso-build-link [run]="run" /></ng-template>
    </dso-grid>
  `,
})
export class RunGrid {
  readonly runs = input.required<readonly PipelineRun[]>();
  readonly label = input.required<string>();
  readonly details = input<readonly RunDetail[]>([]);

  protected readonly columns = computed(() => {
    const shown = new Set<string>(this.details());
    return COLUMNS.filter((column) => !DETAILS.has(column.key) || shown.has(column.key));
  });
  protected readonly runId = (run: PipelineRun) => `${run.job} ${run.build} ${run.time}`;
}
