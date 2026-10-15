import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { DomSanitizer } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MonitoringApi } from '../core/api';
import { errorMessage } from '@common/core/errors';
import { PipelineMonitoring, PipelineRun, pipelineTypeLabel } from '../core/models';
import { BuildLink } from '../shared/build-link';
import {
  CountedPipe,
  DurationPipe,
  RelativeTimePipe,
  TIME_ZONE_NOTE,
  formatDuration,
} from '@common/shared/formatting';
import { RUN_LOOK, StatusChip } from '../shared/status-chip';
import { GRID, GridColumn } from '@common/ui/grid';
import { DsoLoading } from '@common/ui/loading';
import { TOGGLES } from '@common/ui/toggle-group';
import { ActivityChart } from './activity-chart';
import { DoraTiles } from './dora-tiles';
import { MetricsBanner } from './metrics-banner';
import { stageSummary } from './stages';

const RANGES = ['7d', '30d', '90d', '180d'].map((value) => ({
  value,
  label: `${value.slice(0, -1)} days`,
}));

@Component({
  selector: 'dso-pipeline-monitoring',
  imports: [
    DatePipe,
    RouterLink,
    GRID,
    DsoLoading,
    TOGGLES,
    ActivityChart,
    BuildLink,
    CountedPipe,
    DoraTiles,
    DurationPipe,
    MetricsBanner,
    RelativeTimePipe,
    StatusChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pipeline-monitoring.html',
  styleUrl: './pipeline-monitoring.scss',
})
export class PipelineMonitoringPage {
  readonly id = input.required<string>();
  readonly range = input<string>();

  private readonly api = inject(MonitoringApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly ranges = RANGES;
  protected readonly selectedRange = computed(
    () => RANGES.find((range) => range.value === this.range())?.value ?? '30d',
  );

  protected readonly resource = rxResource({
    params: () => ({ id: Number(this.id()), range: this.selectedRange() }),
    stream: ({ params }) => this.api.pipeline(params.id, params.range),
  });
  protected readonly data = linkedSignal<
    PipelineMonitoring | undefined,
    PipelineMonitoring | undefined
  >({
    source: () => (this.resource.hasValue() ? this.resource.value() : undefined),
    computation: (next, previous) => next ?? previous?.value,
  });

  protected readonly dashboards = computed(() =>
    (this.data()?.grafana ?? []).map((dashboard) => ({
      ...dashboard,
      embedded: this.sanitizer.bypassSecurityTrustResourceUrl(`${dashboard.dashboardUrl}&kiosk`),
    })),
  );

  protected readonly runId = (run: PipelineRun) => `${run.time} ${run.build}`;
  protected readonly runColumns: GridColumn<PipelineRun>[] = [
    { key: 'time', header: 'Finished', value: (run) => run.time, width: 130 },
    { key: 'result', header: 'Result', value: (run) => RUN_LOOK[run.result]?.label, width: 140 },
    { key: 'build', header: 'Build', value: (run) => run.build, width: 90 },
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
      value: (run) => (run.stagesTotal ? `${run.passed ?? 0} of ${run.stagesTotal}` : '–'),
      width: 140,
    },
  ];
  protected readonly errorMessage = errorMessage;

  protected readonly typeLabel = pipelineTypeLabel;
  protected readonly stageSummary = stageSummary;
  protected readonly timeZoneNote = TIME_ZONE_NOTE;

  protected selectRange(range: string): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: { range }, replaceUrl: true });
  }
}
