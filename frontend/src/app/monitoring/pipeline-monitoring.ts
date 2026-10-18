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
import { PipelineMonitoring, pipelineTypeLabel } from '../core/models';
import { MONITORING, monitoringProduct, pipelinePage } from '../core/sections';
import { BuildLink } from '../shared/build-link';
import {
  CountedPipe,
  DurationPipe,
  RelativeTimePipe,
  TIME_ZONE_NOTE,
} from '@common/shared/formatting';
import { StatusChip } from '../shared/status-chip';
import { LoadError } from '@common/ui/load-error';
import { DsoLoading } from '@common/ui/loading';
import { TOGGLES } from '@common/ui/toggle-group';
import { ActivityChart } from './activity-chart';
import { DoraTiles } from './dora-tiles';
import { MetricsBanner } from './metrics-banner';
import { RunGrid } from './run-grid';
import { stageSummary, stagesPassed } from './stages';

const RANGES = ['7d', '30d', '90d', '180d'].map((value) => ({
  value,
  label: `${value.slice(0, -1)} days`,
}));

@Component({
  selector: 'dso-pipeline-monitoring',
  imports: [
    DatePipe,
    RouterLink,
    DsoLoading,
    LoadError,
    TOGGLES,
    ActivityChart,
    BuildLink,
    CountedPipe,
    DoraTiles,
    DurationPipe,
    MetricsBanner,
    RelativeTimePipe,
    RunGrid,
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

  protected readonly section = MONITORING;
  protected readonly monitoringProduct = monitoringProduct;
  protected readonly pipelinePage = pipelinePage;
  protected readonly typeLabel = pipelineTypeLabel;
  protected readonly stagesPassed = stagesPassed;
  protected readonly stageSummary = stageSummary;
  protected readonly timeZoneNote = TIME_ZONE_NOTE;

  protected selectRange(range: string): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: { range }, replaceUrl: true });
  }
}
