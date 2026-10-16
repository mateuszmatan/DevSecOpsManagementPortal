import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActionHistoryApi } from '../core/api';
import { ActionLog } from '../core/models';
import { ACTION_HISTORY } from '../core/sections';
import { text } from '@common/shared/form-controls';
import { FORM_FIELD } from '@common/ui/form-field';
import { GRID, GridColumn } from '@common/ui/grid';
import { DsoLoading } from '@common/ui/loading';

export const ACTION_OPTIONS: { value: string; label: string }[] = [
  { value: 'ALL', label: 'Wszystkie akcje' },
  { value: 'CREATE_PRODUCT', label: 'Utworzenie produktu' },
  { value: 'UPDATE_PRODUCT', label: 'Aktualizacja produktu/serwisów' },
  { value: 'DELETE_PRODUCT', label: 'Usunięcie produktu' },
  { value: 'CREATE_PIPELINE', label: 'Utworzenie pipeline' },
  { value: 'UPDATE_PIPELINE', label: 'Aktualizacja pipeline' },
  { value: 'DELETE_PIPELINE', label: 'Usunięcie pipeline' },
  { value: 'ROTATE_KEY', label: 'Rotacja / wydanie klucza' },
  { value: 'REVOKE_KEY', label: 'Unieważnienie klucza' },
  { value: 'INITIALIZE_PORTAL', label: 'Inicjalizacja portalu' },
];

export const RESOURCE_OPTIONS: { value: string; label: string }[] = [
  { value: 'ALL', label: 'Wszystkie zasoby' },
  { value: 'PRODUCT', label: 'Produkt' },
  { value: 'SERVICE', label: 'Serwis' },
  { value: 'PIPELINE', label: 'Pipeline' },
  { value: 'PIPELINE_KEY', label: 'Klucz pipeline' },
  { value: 'PORTAL', label: 'Portal' },
];

export const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: 'ALL', label: 'Wszystkie statusy' },
  { value: 'SUCCESS', label: 'Sukces' },
  { value: 'FAILURE', label: 'Błąd' },
];

@Component({
  selector: 'dso-action-history',
  imports: [ReactiveFormsModule, DatePipe, FORM_FIELD, GRID, DsoLoading],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>{{ section.heading }}</h1>
          <p class="page-description">{{ section.description }}</p>
        </div>
        <div class="actions">
          <button type="button" class="btn btn-outline-primary" (click)="reload()">Odśwież</button>
        </div>
      </header>

      <section class="card toolbar">
        <form [formGroup]="filters" class="filters-row">
          <dso-form-field class="search-field">
            <dso-label>Szukaj</dso-label>
            <input dsoInput formControlName="search" placeholder="Użytkownik, zasób, szczegóły..." autocomplete="off" />
          </dso-form-field>

          <dso-form-field class="select-field">
            <dso-label>Akcja</dso-label>
            <select dsoInput formControlName="actionType">
              @for (option of actionOptions; track option.value) {
                <option [value]="option.value">{{ option.label }}</option>
              }
            </select>
          </dso-form-field>

          <dso-form-field class="select-field">
            <dso-label>Zasób</dso-label>
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
        <div class="banner danger" role="alert">
          Nie udało się załadować historii akcji.
          <button type="button" class="btn btn-outline-primary" (click)="reload()">Spróbuj ponownie</button>
        </div>
      } @else {
        @if (filteredRows().length) {
          <section class="card list">
            <header class="list-head">
              <span class="shown">Liczba operacji: {{ filteredRows().length }}</span>
            </header>
            <dso-grid
              [rows]="filteredRows()"
              [columns]="columns"
              [rowId]="rowId"
            >
              <ng-template dsoCell="time" let-row>
                <span class="time-cell">{{ row.createdAt | date: 'd MMM y, HH:mm:ss' }}</span>
              </ng-template>
              <ng-template dsoCell="user" let-row>
                <div class="user-cell">
                  <strong>{{ row.userDisplayName || row.username }}</strong>
                  @if (row.userEmail) {
                    <span class="muted email">{{ row.userEmail }}</span>
                  }
                </div>
              </ng-template>
              <ng-template dsoCell="resource" let-row>
                <span class="badge resource-tag">{{ row.resourceType }}</span>
                <span class="resource-name mono">{{ row.resourceName || '—' }}</span>
              </ng-template>
              <ng-template dsoCell="status" let-row>
                @if (row.status === 'SUCCESS') {
                  <span class="chip success">Sukces</span>
                } @else {
                  <span class="chip danger">Błąd</span>
                }
              </ng-template>
            </dso-grid>
          </section>
        } @else {
          <div class="card empty-state">
            <h3>Brak wpisów w historii akcji</h3>
            <p>Wszystkie akcje użytkowników dotyczące produktów, serwisów, pipelineów i kluczy będą rejestrowane tutaj.</p>
          </div>
        }
      }
    </div>
  `,
  styles: `
    .toolbar {
      margin-bottom: 16px;
      padding: 12px 16px;
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

    .list-head {
      margin-bottom: 8px;
    }

    .shown {
      font-size: 13px;
      color: var(--dso-muted);
      font-weight: 500;
    }

    .time-cell {
      white-space: nowrap;
      font-size: 12.5px;
    }

    .user-cell {
      display: flex;
      flex-direction: column;
      gap: 2px;

      .email {
        font-size: 11px;
      }
    }

    .resource-tag {
      background: var(--dso-surface-variant, #e2e8f0);
      color: var(--dso-on-surface-variant, #334155);
      padding: 2px 6px;
      border-radius: 4px;
      font-size: 10.5px;
      font-weight: 600;
      margin-right: 6px;
    }

    .resource-name {
      font-size: 12.5px;
    }

    .chip {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 12px;
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.03em;

      &.success {
        background: #e6f4ea;
        color: #137333;
      }

      &.danger {
        background: #fce8e6;
        color: #c5221f;
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
      header: 'Kiedy',
      sortValue: (row) => Date.parse(row.createdAt) || 0,
      width: 170,
    },
    {
      key: 'user',
      header: 'Użytkownik',
      value: (row) => row.userDisplayName || row.username,
      sortValue: (row) => (row.userDisplayName || row.username).toLowerCase(),
      width: 190,
    },
    {
      key: 'department',
      header: 'Dział',
      value: (row) => row.departmentName || '—',
      sortValue: (row) => (row.departmentName || '').toLowerCase(),
      width: 150,
    },
    {
      key: 'action',
      header: 'Akcja',
      value: (row) => row.actionType,
      sortValue: (row) => row.actionType,
      width: 160,
    },
    {
      key: 'resource',
      header: 'Zasób',
      value: (row) => `${row.resourceType}: ${row.resourceName || ''}`,
      sortValue: (row) => `${row.resourceType} ${row.resourceName || ''}`.toLowerCase(),
      width: 200,
    },
    {
      key: 'details',
      header: 'Szczegóły operacji',
      value: (row) => row.details || '—',
      minWidth: 240,
    },
    {
      key: 'status',
      header: 'Efekt',
      value: (row) => row.status,
      sortValue: (row) => row.status,
      width: 100,
    },
  ];

  protected readonly rowId = (row: ActionLog) => String(row.id);

  protected readonly filteredRows = computed(() => {
    if (!this.logsResource.hasValue()) {
      return [];
    }
    const all = this.logsResource.value();
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
          item.actionType,
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

  protected reload(): void {
    this.logsResource.reload();
  }
}
