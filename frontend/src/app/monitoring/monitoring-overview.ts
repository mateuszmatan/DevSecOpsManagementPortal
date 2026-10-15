import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { MonitoringApi } from '../core/api';
import { RETRY, errorMessage } from '@common/core/errors';
import { ProductHealth, RunResult } from '../core/models';
import { MONITORING } from '../core/sections';
import { byDepartment } from '@common/departments/departments';
import { BarChart, BarRow } from '../shared/bar-chart';
import { CountedPipe, RelativeTimePipe, counted } from '@common/shared/formatting';
import { StatusChip } from '../shared/status-chip';
import { FORM_FIELD } from '@common/ui/form-field';
import { DsoLoading } from '@common/ui/loading';
import { ActivityChart } from './activity-chart';
import { DoraTiles } from './dora-tiles';
import { MetricsBanner } from './metrics-banner';
import { STATUS_ORDER, StatusBar } from './status-bar';
import { DepartmentsApi } from '@common/core/api';

const ACTIVITY_RANGE = '30d';

@Component({
  selector: 'dso-monitoring-overview',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    FORM_FIELD,
    DsoLoading,
    ActivityChart,
    BarChart,
    CountedPipe,
    DoraTiles,
    MetricsBanner,
    RelativeTimePipe,
    StatusBar,
    StatusChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './monitoring-overview.html',
  styleUrl: './monitoring-overview.scss',
})
export class MonitoringOverview {
  private readonly api = inject(MonitoringApi);
  private readonly departmentsApi = inject(DepartmentsApi);

  protected readonly section = MONITORING;
  protected readonly status = rxResource({ stream: () => this.api.status() });
  protected readonly overview = rxResource({ stream: () => this.api.overview() });
  protected readonly departments = rxResource({ stream: () => this.departmentsApi.list() });
  protected readonly activity = rxResource({ stream: () => this.api.activity(ACTIVITY_RANGE) });

  protected readonly search = new FormControl('', { nonNullable: true });
  private readonly query = toSignal(
    this.search.valueChanges.pipe(map((value) => value.trim().toLowerCase())),
    {
      initialValue: '',
    },
  );

  protected readonly products = computed(() => {
    if (!this.overview.hasValue()) {
      return [];
    }
    const query = this.query();
    return this.overview
      .value()
      .products.filter(
        (product) =>
          !query ||
          [product.name, product.code, product.ownerTeam ?? ''].some((value) =>
            value.toLowerCase().includes(query),
          ),
      );
  });

  protected readonly groups = computed(() =>
    this.departments.hasValue()
      ? byDepartment(this.departments.value(), this.products()).filter(
          (group) => group.products.length,
        )
      : [],
  );

  protected readonly departmentStatus = computed<BarRow[]>(() =>
    this.overview.hasValue() && this.departments.hasValue()
      ? byDepartment(this.departments.value(), this.overview.value().products).map((group) => {
          const counts = statusTotals(group.products);
          return {
            label: group.name,
            note: `${counted(pipelineCount(counts), 'pipeline')} · ${counted(group.products.length, 'product')}`,
            segments: STATUS_ORDER.map((entry) => ({
              swatch: entry.status.toLowerCase(),
              label: entry.label.toLowerCase(),
              count: counts[entry.status] ?? 0,
            })),
          };
        })
      : [],
  );

  protected readonly tiles = computed(() => {
    const totals = statusTotals(this.overview.hasValue() ? this.overview.value().products : []);
    return [
      { label: 'Pipelines', value: pipelineCount(totals), tone: 'info' },
      { label: 'Passed', value: totals.SUCCESS ?? 0, tone: 'success' },
      {
        label: 'Failed or passed with warnings',
        value: (totals.FAILURE ?? 0) + (totals.UNSTABLE ?? 0),
        tone: 'danger',
      },
      { label: 'Keys invalidated', value: totals.DISABLED ?? 0, tone: 'neutral' },
    ];
  });

  protected readonly activityProblem = computed(() => {
    const error = this.activity.error();
    if (error) {
      return errorMessage(error);
    }
    const metricsError = this.activity.hasValue() ? this.activity.value().metricsError : null;
    const configured = !this.status.hasValue() || this.status.value().influxConfigured;
    return metricsError && configured
      ? `The run results could not be read (${metricsError}). ${RETRY}`
      : null;
  });

  protected readonly statusOrder = STATUS_ORDER;
  protected readonly errorMessage = errorMessage;

  protected refresh(): void {
    this.status.reload();
    this.overview.reload();
    this.departments.reload();
    this.activity.reload();
  }
}

function statusTotals(products: readonly ProductHealth[]): Partial<Record<RunResult, number>> {
  const totals: Partial<Record<RunResult, number>> = {};
  for (const product of products) {
    for (const [status, count] of Object.entries(product.statusCounts) as [RunResult, number][]) {
      totals[status] = (totals[status] ?? 0) + count;
    }
  }
  return totals;
}

function pipelineCount(counts: Partial<Record<RunResult, number>>): number {
  return Object.values(counts).reduce((sum, count) => sum + (count ?? 0), 0);
}
