import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { MonitoringApi } from '../core/api';
import { errorMessage } from '@common/core/errors';
import { PipelineHealth, RunResult, pipelineTypeLabel } from '../core/models';
import { BuildLink } from '../shared/build-link';
import { CountedPipe, RelativeTimePipe, formatDuration } from '@common/shared/formatting';
import { RUN_LOOK, StatusChip } from '../shared/status-chip';
import { GRID, GridColumn } from '@common/ui/grid';
import { DsoLoading } from '@common/ui/loading';
import { MetricsBanner } from './metrics-banner';
import { stageProblems } from './stages';
import { StatusBar, statusSummary } from './status-bar';

@Component({
  selector: 'dso-product-monitoring',
  imports: [
    RouterLink,
    GRID,
    DsoLoading,
    BuildLink,
    CountedPipe,
    MetricsBanner,
    RelativeTimePipe,
    StatusBar,
    StatusChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-monitoring.html',
  styleUrl: './product-monitoring.scss',
})
export class ProductMonitoringPage {
  readonly id = input.required<string>();

  private readonly api = inject(MonitoringApi);
  private readonly router = inject(Router);

  protected readonly product = rxResource({
    params: () => Number(this.id()),
    stream: ({ params }) => this.api.product(params),
  });

  protected readonly counts = computed(() => {
    const counts: Partial<Record<RunResult, number>> = {};
    if (this.product.hasValue()) {
      for (const health of this.product.value().pipelines) {
        counts[health.status] = (counts[health.status] ?? 0) + 1;
      }
    }
    return counts;
  });
  protected readonly summary = computed(() => statusSummary(this.counts()));

  protected readonly typeLabel = pipelineTypeLabel;
  protected readonly pipelineId = (health: PipelineHealth) => health.pipeline.id;
  protected readonly clickable = () => 'clickable';
  protected readonly columns: GridColumn<PipelineHealth>[] = [
    {
      key: 'service',
      header: 'Service',
      value: (health) => health.pipeline.serviceName,
      wrap: true,
      minWidth: 130,
    },
    {
      key: 'type',
      header: 'Pipeline',
      value: (health) => pipelineTypeLabel(health.pipeline.type),
      wrap: true,
      minWidth: 180,
    },
    {
      key: 'status',
      header: 'Status',
      value: (health) => RUN_LOOK[health.status]?.label,
      width: 150,
    },
    {
      key: 'lastRun',
      header: 'Latest run',
      value: (health) => health.lastRun?.time ?? '',
      wrap: true,
      minWidth: 180,
    },
    {
      key: 'duration',
      header: 'Duration',
      value: (health) => formatDuration(health.lastRun?.durationSeconds),
      sortValue: (health) => health.lastRun?.durationSeconds ?? -1,
      width: 110,
    },
    {
      key: 'stages',
      header: 'Stages',
      value: (health) =>
        health.lastRun?.stagesTotal
          ? `${health.lastRun.passed ?? 0} of ${health.lastRun.stagesTotal} passed`
          : '–',
      wrap: true,
      width: 150,
    },
    { key: 'jenkins', header: 'Jenkins', width: 140 },
  ];
  protected readonly errorMessage = errorMessage;
  protected readonly stageProblems = stageProblems;

  protected open(health: PipelineHealth): void {
    this.router.navigate(['/monitoring/pipelines', health.pipeline.id]);
  }
}
