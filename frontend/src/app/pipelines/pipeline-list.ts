import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { MyDepartment } from '@common/departments/my-department';
import { PipelinesApi } from '../core/api';
import { errorMessage } from '@common/core/errors';
import {
  PIPELINE_TYPES,
  Pipeline,
  PipelineHealth,
  PipelineType,
  RunResult,
  pipelineTypeLabel,
} from '../core/models';
import { PIPELINES, SELF_SERVICE, pipelinePage } from '../core/sections';
import { MetricsBanner } from '../monitoring/metrics-banner';
import { text } from '@common/shared/form-controls';
import { counted, formatRelative } from '@common/shared/formatting';
import { RUN_LOOK, StatusChip } from '../shared/status-chip';
import { FORM_FIELD } from '@common/ui/form-field';
import { GRID, GridColumn } from '@common/ui/grid';
import { DsoLoading } from '@common/ui/loading';
import { PipelineActions } from './pipeline-actions';
import { KEY_MEANING } from './pipeline-texts';
import { DepartmentsApi } from '@common/core/api';

export type KeyFilter = 'ALL' | 'ACTIVE' | 'INVALIDATED';

export interface PipelineFilters {
  service: string;
  product: string;
  type: PipelineType | 'ALL';
  job: string;
  key: KeyFilter;
  status: RunResult | 'ALL';
}

export const KEY_FILTERS: { value: KeyFilter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INVALIDATED', label: 'Invalidated' },
];

export const TYPE_FILTERS: { value: PipelineType | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'All' },
  ...PIPELINE_TYPES.map(({ value, label }) => ({ value, label })),
];

export const STATUS_FILTERS: { value: RunResult | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'All' },
  ...(Object.keys(RUN_LOOK) as RunResult[]).map((value) => ({
    value,
    label: RUN_LOOK[value].label,
  })),
];

const contains = (value: string | null, typed: string) =>
  (value ?? '').toLowerCase().includes(typed.trim().toLowerCase());

export function matches(row: PipelineHealth, filters: PipelineFilters): boolean {
  const { pipeline } = row;
  const active = pipeline.activeKey !== null;
  return (
    contains(pipeline.serviceName, filters.service) &&
    (contains(pipeline.productName, filters.product) ||
      contains(pipeline.productCode, filters.product)) &&
    (filters.type === 'ALL' || pipeline.type === filters.type) &&
    contains(pipeline.jenkinsJob, filters.job) &&
    (filters.key === 'ALL' || (filters.key === 'ACTIVE') === active) &&
    (filters.status === 'ALL' || row.status === filters.status)
  );
}

@Component({
  selector: 'dso-pipeline-list',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    FORM_FIELD,
    GRID,
    DsoLoading,
    MetricsBanner,
    StatusChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>{{ section.heading }}</h1>
          <p class="page-description">{{ section.description }}</p>
        </div>
        <div class="actions">
          <a class="btn btn-primary" [routerLink]="selfService.path"
            >Set up pipelines in Self-service</a
          >
        </div>
      </header>
      <section class="card toolbar">
        <dso-form-field class="department">
          <dso-label>Your department</dso-label>
          <select dsoInput [formControl]="department">
            <option [ngValue]="null" disabled>Choose your department</option>
            @for (department of departmentList(); track department.id) {
              <option [ngValue]="department.id">{{ department.name }}</option>
            }
          </select>
          <dso-hint
            >Only the pipelines of this department are listed. This browser remembers your
            choice.</dso-hint
          >
        </dso-form-field>
      </section>
      @if (departments.error(); as error) {
        <div class="banner" role="alert">
          <span class="banner-text"
            >The departments could not be loaded. {{ errorMessage(error) }}</span
          >
          <button type="button" class="btn btn-outline-primary" (click)="departments.reload()">
            Try again
          </button>
        </div>
      }
      @if (departmentId() === null) {
        <section class="card empty-state">
          <h3>Choose your department to see its pipelines</h3>
          <p>Pick it under Your department above.</p>
        </section>
      } @else {
        @if (pipelines.isLoading()) {
          <dso-loading />
        }
        @if (pipelines.error(); as error) {
          <div class="banner" role="alert">
            <span class="banner-text"
              >The pipelines could not be loaded. {{ errorMessage(error) }}</span
            >
            <button type="button" class="btn btn-outline-primary" (click)="pipelines.reload()">
              Try again
            </button>
          </div>
        }
        @if (pipelines.hasValue()) {
          <dso-metrics-banner [metricsError]="pipelines.value().metricsError" />
          @if (all().length) {
            <section class="card list">
              <header class="card-header">
                <h2>Pipelines of {{ departmentName() }}</h2>
                <span class="muted shown">{{ shown() }}</span>
              </header>
              <p class="section-help">
                Pipeline key: {{ keyMeaning }}. Only its first and last characters are shown here;
                Invalidated means the pipeline is refused its settings until a new key is issued.
              </p>
              <dso-grid
                label="Pipelines"
                empty="No pipeline matches the filters."
                [rows]="rows()"
                [columns]="columns"
                [rowId]="rowId"
                [rowClass]="rowClass"
                (rowClick)="open($event.pipeline)"
              >
                <ng-template dsoCell="service" let-row>
                  <a class="mono quiet-link" [routerLink]="pipelineLink(row.pipeline.id)">{{
                    row.pipeline.serviceName
                  }}</a>
                </ng-template>
                <ng-template dsoCell="product" let-row>
                  {{ row.pipeline.productName }}
                  <span class="muted mono">{{ row.pipeline.productCode }}</span>
                </ng-template>
                <ng-template dsoCell="key" let-row>
                  @if (row.pipeline.activeKey; as key) {
                    <span class="mono">{{ key.hint }}</span>
                  } @else {
                    <span class="chip danger">Invalidated</span>
                  }
                </ng-template>
                <ng-template dsoCell="status" let-row>
                  <dso-status-chip [status]="row.status" />
                </ng-template>
                <ng-template dsoCell="actions" let-row>
                  <button
                    type="button"
                    class="btn btn-link"
                    [attr.aria-label]="'Edit the settings of ' + label(row.pipeline)"
                    (click)="edit(row.pipeline)"
                  >
                    Edit
                  </button>
                </ng-template>
              </dso-grid>
            </section>
          } @else {
            <div class="card empty-state">
              <h3>No pipelines in {{ departmentName() }} yet</h3>
              <p>
                Self-service sets up the pipelines of a product in a few guided steps and gives each
                service its pipeline key and its Jenkinsfile.
              </p>
              <a class="btn btn-primary" [routerLink]="selfService.path"
                >Set up pipelines in Self-service</a
              >
            </div>
          }
        }
      }
    </div>
  `,
  styles: `
    .toolbar {
      margin-bottom: 12px;
      padding: 10px 12px;
    }

    .department select {
      width: 280px;
      max-width: 100%;
    }

    .banner-text {
      flex: 1;
    }

    .list {
      padding: 10px 14px 0;
    }

    .shown {
      font-size: 12px;
    }
  `,
})
export class PipelineList {
  private readonly api = inject(PipelinesApi);
  private readonly departmentsApi = inject(DepartmentsApi);
  private readonly actions = inject(PipelineActions);
  private readonly router = inject(Router);
  private readonly myDepartment = inject(MyDepartment);

  protected readonly section = PIPELINES;
  protected readonly selfService = SELF_SERVICE;
  protected readonly pipelineLink = pipelinePage;
  protected readonly errorMessage = errorMessage;
  protected readonly keyMeaning = KEY_MEANING;

  protected readonly departmentId = this.myDepartment.departmentId;
  protected readonly department = new FormControl(this.departmentId());
  protected readonly departments = rxResource({ stream: () => this.departmentsApi.list() });
  protected readonly departmentList = computed(() =>
    this.departments.hasValue() ? this.departments.value() : [],
  );
  protected readonly departmentName = computed(
    () =>
      this.departmentList().find((department) => department.id === this.departmentId())?.name ??
      'your department',
  );

  protected readonly pipelines = rxResource({
    params: () => this.departmentId() ?? undefined,
    stream: ({ params }) => this.api.listForDepartment(params),
  });

  protected readonly filters = new FormGroup({
    service: text(''),
    product: text(''),
    type: new FormControl<PipelineType | 'ALL'>('ALL', { nonNullable: true }),
    job: text(''),
    key: new FormControl<KeyFilter>('ALL', { nonNullable: true }),
    status: new FormControl<RunResult | 'ALL'>('ALL', { nonNullable: true }),
  });
  private readonly filterValue = toSignal(
    this.filters.valueChanges.pipe(map(() => this.filters.getRawValue())),
    { initialValue: this.filters.getRawValue() },
  );

  protected readonly rowId = (row: PipelineHealth) => row.pipeline.id;
  protected readonly rowClass = (row: PipelineHealth) =>
    row.pipeline.activeKey ? 'clickable' : ['clickable', 'muted'];
  protected readonly columns: GridColumn<PipelineHealth>[] = [
    {
      key: 'service',
      header: 'Service',
      value: (row) => row.pipeline.serviceName,
      filter: { control: this.filters.controls.service, label: 'service' },
      minWidth: 140,
    },
    {
      key: 'product',
      header: 'Product',
      value: (row) => row.pipeline.productName,
      filter: { control: this.filters.controls.product, label: 'product' },
      flex: 1.5,
      minWidth: 190,
    },
    {
      key: 'type',
      header: 'Type',
      value: (row) => pipelineTypeLabel(row.pipeline.type),
      sortValue: (row) => PIPELINE_TYPES.findIndex((type) => type.value === row.pipeline.type),
      filter: { control: this.filters.controls.type, label: 'type', options: TYPE_FILTERS },
      minWidth: 140,
    },
    {
      key: 'job',
      header: 'Jenkins job',
      value: (row) => row.pipeline.jenkinsJob ?? 'Not set',
      sortValue: (row) => row.pipeline.jenkinsJob ?? '',
      filter: { control: this.filters.controls.job, label: 'Jenkins job' },
      cellClass: 'mono',
      wrap: true,
      flex: 2,
      minWidth: 200,
    },
    {
      key: 'key',
      header: 'Pipeline key',
      value: (row) => row.pipeline.activeKey?.hint ?? 'Invalidated',
      sortValue: (row) => (row.pipeline.activeKey ? 0 : 1),
      filter: { control: this.filters.controls.key, label: 'pipeline key', options: KEY_FILTERS },
      minWidth: 130,
    },
    {
      key: 'status',
      header: 'Last run',
      value: (row) => RUN_LOOK[row.status].label,
      sortValue: (row) => Object.keys(RUN_LOOK).indexOf(row.status),
      filter: { control: this.filters.controls.status, label: 'last run', options: STATUS_FILTERS },
      minWidth: 170,
    },
    {
      key: 'lastRun',
      header: 'Finished',
      value: (row) => (row.lastRun ? formatRelative(row.lastRun.time) : ''),
      sortValue: (row) => Date.parse(row.lastRun?.time ?? '') || 0,
      width: 120,
    },
    { key: 'actions', header: '', width: 80 },
  ];

  protected readonly all = computed(() =>
    this.pipelines.hasValue() ? this.pipelines.value().pipelines : [],
  );
  protected readonly rows = computed(() =>
    this.all().filter((row) => matches(row, this.filterValue())),
  );
  protected readonly shown = computed(() => {
    const all = counted(this.all().length, 'pipeline');
    return this.rows().length === this.all().length ? all : `${this.rows().length} of ${all} shown`;
  });

  constructor() {
    this.department.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((id) => this.myDepartment.choose(id));
  }

  protected label(pipeline: Pipeline): string {
    return `the ${pipelineTypeLabel(pipeline.type)} pipeline of ${pipeline.serviceName}`;
  }

  protected open(pipeline: Pipeline): void {
    this.router.navigate(pipelinePage(pipeline.id));
  }

  protected edit(pipeline: Pipeline): void {
    this.actions.edit(pipeline).subscribe((updated) =>
      this.pipelines.update(
        (listed) =>
          listed && {
            ...listed,
            pipelines: listed.pipelines.map((row) =>
              row.pipeline.id === updated.id
                ? { ...row, pipeline: { ...updated, activeKey: row.pipeline.activeKey } }
                : row,
            ),
          },
      ),
    );
  }
}
