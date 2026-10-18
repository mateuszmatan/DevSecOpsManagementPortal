import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActionHistoryApi } from '../core/api';
import { ActionLog } from '../core/models';
import { ACTION_HISTORY } from '../core/sections';
import { text } from '@common/shared/form-controls';
import { Option, counted, labelOf } from '@common/shared/formatting';
import { FORM_FIELD } from '@common/ui/form-field';
import { GRID, GridColumn } from '@common/ui/grid';
import { LoadError } from '@common/ui/load-error';
import { DsoLoading } from '@common/ui/loading';

export const ACTION_OPTIONS: Option<string>[] = [
  { value: 'ALL', label: 'All actions' },
  { value: 'CREATE_PRODUCT', label: 'Create product' },
  { value: 'UPDATE_PRODUCT', label: 'Update product' },
  { value: 'DELETE_PRODUCT', label: 'Delete product' },
  { value: 'CREATE_PIPELINE', label: 'Create pipeline' },
  { value: 'UPDATE_PIPELINE', label: 'Update pipeline' },
  { value: 'DELETE_PIPELINE', label: 'Delete pipeline' },
  { value: 'ROTATE_KEY', label: 'Issue new key' },
  { value: 'REVOKE_KEY', label: 'Invalidate key' },
  { value: 'CREATE_DEPARTMENT', label: 'Create business service' },
  { value: 'RENAME_DEPARTMENT', label: 'Rename business service' },
  { value: 'DELETE_DEPARTMENT', label: 'Delete business service' },
  { value: 'UPDATE_SERVICE_TEMPLATE', label: 'Update service template' },
  { value: 'UPDATE_GLOBAL_SETTINGS', label: 'Update library defaults' },
  { value: 'INITIALIZE_PORTAL', label: 'Initialize portal' },
];

export const RESOURCE_OPTIONS: Option<string>[] = [
  { value: 'ALL', label: 'All resources' },
  { value: 'PRODUCT', label: 'Product' },
  { value: 'PIPELINE', label: 'Pipeline' },
  { value: 'PIPELINE_KEY', label: 'Pipeline key' },
  { value: 'DEPARTMENT', label: 'Business service' },
  { value: 'SETTINGS', label: 'Settings' },
  { value: 'PORTAL', label: 'Portal' },
];

export const STATUS_OPTIONS: Option<string>[] = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'SUCCESS', label: 'Success' },
  { value: 'FAILURE', label: 'Failure' },
];

@Component({
  selector: 'dso-action-history',
  imports: [ReactiveFormsModule, DatePipe, FORM_FIELD, GRID, DsoLoading, LoadError],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>{{ section.heading }}</h1>
          <p class="page-description">{{ section.description }}</p>
        </div>
        <div class="actions">
          <button type="button" class="btn btn-outline-primary" (click)="reload()">Refresh</button>
        </div>
      </header>

      <section class="card toolbar">
        <form [formGroup]="filters" class="filters-row">
          <dso-form-field class="search-field">
            <dso-label>Search</dso-label>
            <input dsoInput formControlName="search" placeholder="User, resource, details..." autocomplete="off" />
          </dso-form-field>

          <dso-form-field class="select-field">
            <dso-label>Action</dso-label>
            <select dsoInput formControlName="actionType">
              @for (option of actionOptions; track option.value) {
                <option [value]="option.value">{{ option.label }}</option>
              }
            </select>
          </dso-form-field>

          <dso-form-field class="select-field">
            <dso-label>Resource</dso-label>
            <select dsoInput formControlName="resourceType">
              @for (option of resourceOptions; track option.value) {
                <option [value]="option.value">{{ option.label }}</option>
              }
            </select>
          </dso-form-field>

          <dso-form-field class="select-field">
            <dso-label>Status</dso-label>
            <select dsoInput formControlName="status">
              @for (option of statusOptions; track option.value) {
                <option [value]="option.value">{{ option.label }}</option>
              }
            </select>
          </dso-form-field>
        </form>
      </section>

      @if (logsResource.isLoading() && !logsResource.hasValue()) {
        <dso-loading />
      } @else if (logsResource.error(); as error) {
        <dso-load-error what="The action history" [error]="error" (retry)="reload()" />
      } @else {
        @if (all().length) {
          <section class="card list">
            <header class="card-header">
              <h2>Actions</h2>
              <span class="muted shown">{{ shown() }}</span>
            </header>
            <dso-grid
              label="Action history"
              empty="No action matches the filters."
              [rows]="filteredRows()"
              [columns]="columns"
              [rowId]="rowId"
            >
              <ng-template dsoCell="time" let-row>
                <span class="time-cell">{{ row.createdAt | date: 'd MMM y, HH:mm:ss' }}</span>
              </ng-template>
              <ng-template dsoCell="user" let-row>
                <strong [title]="row.userEmail ?? ''">{{ row.userDisplayName || row.username }}</strong>
              </ng-template>
              <ng-template dsoCell="resource" let-row>
                <span class="resource-type">{{ resourceLabel(row) }}</span>
                {{ row.resourceName || '–' }}
              </ng-template>
              <ng-template dsoCell="status" let-row>
                <span class="status" [class.failure]="row.status !== 'SUCCESS'">{{ statusLabel(row) }}</span>
              </ng-template>
            </dso-grid>
          </section>
        } @else {
          <div class="card empty-state">
            <h3>No action history entries</h3>
            <p>All user actions on products, services, pipelines and keys will be recorded here.</p>
          </div>
        }
      }
    </div>
  `,
  styles: `
    .toolbar {
      margin-bottom: 12px;
      padding: 10px 12px;
    }

    .filters-row {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      align-items: flex-end;
    }

    .search-field {
      flex: 1 1 240px;
    }

    .select-field {
      flex: 0 1 180px;
    }

    .list {
      padding: 10px 14px 0;
    }

    .shown {
      font-size: 12px;
    }

    .time-cell {
      white-space: nowrap;
      font-size: 12.5px;
    }

    .resource-type {
      color: var(--dso-muted);
      margin-right: 6px;
    }

    .status {
      font-weight: 500;
      color: var(--dso-success);

      &.failure {
        color: var(--dso-danger);
      }
    }
  `,
})
export class ActionHistoryPage {
  private readonly api = inject(ActionHistoryApi);

  protected readonly section = ACTION_HISTORY;
  protected readonly actionOptions = ACTION_OPTIONS;
  protected readonly resourceOptions = RESOURCE_OPTIONS;
  protected readonly statusOptions = STATUS_OPTIONS;

  protected readonly filters = new FormGroup({
    search: text(''),
    actionType: new FormControl<string>('ALL', { nonNullable: true }),
    resourceType: new FormControl<string>('ALL', { nonNullable: true }),
    status: new FormControl<string>('ALL', { nonNullable: true }),
  });

  private readonly filterValue = toSignal(this.filters.valueChanges, {
    initialValue: this.filters.getRawValue(),
  });

  protected readonly logsResource = rxResource({
    stream: () => this.api.list(),
  });

  protected readonly columns: GridColumn<ActionLog>[] = [
    {
      key: 'time',
      header: 'Time',
      sortValue: (row) => Date.parse(row.createdAt) || 0,
      width: 170,
    },
    {
      key: 'user',
      header: 'User',
      value: (row) => row.userDisplayName || row.username,
      sortValue: (row) => (row.userDisplayName || row.username).toLowerCase(),
      width: 190,
    },
    {
      key: 'department',
      header: 'Business Service',
      value: (row) => row.departmentName || '–',
      sortValue: (row) => (row.departmentName || '').toLowerCase(),
      width: 150,
    },
    {
      key: 'action',
      header: 'Action',
      value: (row) => labelOf(ACTION_OPTIONS, row.actionType),
      width: 160,
    },
    {
      key: 'resource',
      header: 'Resource',
      value: (row) => `${this.resourceLabel(row)} ${row.resourceName || ''}`,
      width: 200,
    },
    {
      key: 'details',
      header: 'Details',
      value: (row) => row.details || '–',
      minWidth: 240,
    },
    {
      key: 'status',
      header: 'Status',
      value: (row) => this.statusLabel(row),
      width: 100,
    },
  ];

  protected readonly rowId = (row: ActionLog) => String(row.id);
  protected readonly resourceLabel = (row: ActionLog) => labelOf(RESOURCE_OPTIONS, row.resourceType);
  protected readonly statusLabel = (row: ActionLog) => labelOf(STATUS_OPTIONS, row.status);

  protected readonly all = computed(() =>
    this.logsResource.hasValue() ? this.logsResource.value() : [],
  );

  protected readonly filteredRows = computed(() => {
    const all = this.all();
    const f = this.filterValue();
    const query = (f.search ?? '').trim().toLowerCase();
    const action = f.actionType;
    const resource = f.resourceType;
    const status = f.status;

    return all.filter((item) => {
      if (action && action !== 'ALL' && item.actionType !== action) {
        return false;
      }
      if (resource && resource !== 'ALL' && item.resourceType !== resource) {
        return false;
      }
      if (status && status !== 'ALL' && item.status !== status) {
        return false;
      }
      if (query) {
        const textToSearch = [
          item.username,
          item.userDisplayName,
          item.userEmail,
          item.resourceName,
          item.details,
          item.departmentName,
          labelOf(ACTION_OPTIONS, item.actionType),
        ]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();
        if (!textToSearch.includes(query)) {
          return false;
        }
      }
      return true;
    });
  });

  protected readonly shown = computed(() => {
    const all = counted(this.all().length, 'action');
    return this.filteredRows().length === this.all().length
      ? all
      : `${this.filteredRows().length} of ${all} shown`;
  });

  protected reload(): void {
    this.logsResource.reload();
  }
}
