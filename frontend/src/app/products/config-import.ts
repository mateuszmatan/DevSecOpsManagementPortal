import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { DIALOG_DATA, Dialog } from '@angular/cdk/dialog';
import { Observable, finalize, from, map, of, switchMap, throwError } from 'rxjs';
import { ProductsApi } from '../core/api';
import { ImportedService, ServiceRequest } from '../core/models';
import { errorMessage, fieldProblems } from '@common/core/errors';
import { FieldProblem } from '@common/core/models';
import { Notifier } from '@common/core/notifier';
import { capitalized } from '@common/shared/formatting';
import { DIALOG } from '@common/ui/dialog';

export const MAX_CONFIG_BYTES = 262_144;

export interface ImportedConfig {
  file: string;
  name: string;
  service: Partial<ServiceRequest>;
  notes: FieldProblem[];
  agentLabels: string[] | null;
}

const NOT_IMPORTED = /^not imported\b[:,]?\s*/i;

export function noteGroups(notes: readonly FieldProblem[]) {
  const group = (title: string, imported: boolean) => ({
    title,
    notes: notes
      .filter((note) => NOT_IMPORTED.test(note.message) !== imported)
      .map((note) => ({ ...note, message: capitalized(note.message.replace(NOT_IMPORTED, '')) })),
  });
  return [group('Not imported', false), group('Check', true)].filter(
    (candidate) => candidate.notes.length,
  );
}

@Component({
  selector: 'dso-config-service-picker',
  imports: [DIALOG],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 dsoDialogTitle>Choose the service to import</h2>
    </div>
    <div class="modal-body">
      <p>The file describes {{ services.length }} services. Choose the one to import.</p>
      <div class="choices">
        @for (service of services; track $index) {
          <button type="button" class="btn btn-outline-primary mono" [dsoDialogClose]="service">
            {{ service.name || 'Unnamed service' }}
          </button>
        }
      </div>
    </div>
    <div class="modal-footer">
      <button type="button" class="btn btn-link" dsoDialogClose>Cancel</button>
    </div>
  `,
  styles: `
    .modal-body {
      width: min(420px, 80vw);
    }
    p {
      margin: 0 0 10px;
    }
    .choices {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
  `,
})
export class ConfigServicePicker {
  protected readonly services = inject<readonly ImportedService[]>(DIALOG_DATA);
}

@Component({
  selector: 'dso-config-import',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'dso-config-import' },
  template: `
    <button
      type="button"
      class="btn btn-outline-primary"
      [disabled]="disabled() || busy()"
      (click)="file.click()"
    >
      Import config.yaml
    </button>
    <input
      #file
      type="file"
      accept=".yaml,.yml,.json"
      hidden
      aria-label="config.yaml to import"
      (change)="read(file)"
    />
  `,
})
export class ConfigImport {
  readonly disabled = input(false);
  readonly serviceName = input<string | null>(null);
  readonly imported = output<ImportedConfig>();

  private readonly api = inject(ProductsApi);
  private readonly dialog = inject(Dialog);
  private readonly notifier = inject(Notifier);

  protected readonly busy = signal(false);

  protected read(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > MAX_CONFIG_BYTES) {
      this.notifier.error(`${file.name} is larger than 256 KiB and cannot be imported.`);
      return;
    }
    this.busy.set(true);
    from(file.text())
      .pipe(
        switchMap((content) =>
          this.api.importServiceConfig(content, this.serviceName()?.trim() || null),
        ),
        switchMap((result) =>
          this.choose(result.services).pipe(map((chosen) => chosen && { result, chosen })),
        ),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (picked) =>
          picked &&
          this.imported.emit({
            file: file.name,
            name: picked.chosen.name ?? '',
            service: picked.chosen.service,
            notes: [...picked.result.notes, ...picked.chosen.notes],
            agentLabels: picked.result.pipeline?.agentLabels ?? null,
          }),
        error: (error) => this.notifier.error(this.failure(file.name, error)),
      });
  }

  private choose(services: readonly ImportedService[]): Observable<ImportedService | undefined> {
    if (services.length < 2) {
      return services.length
        ? of(services[0])
        : throwError(() => new Error('The file describes no service.'));
    }
    return this.dialog.open<ImportedService, readonly ImportedService[], ConfigServicePicker>(
      ConfigServicePicker,
      { data: services },
    ).closed;
  }

  private failure(file: string, error: unknown): string {
    const problem = fieldProblems(error).find(({ field }) => field === 'content');
    return `${file} could not be imported. ${problem ? `${capitalized(problem.message)}.` : errorMessage(error)}`;
  }
}

@Component({
  selector: 'dso-import-notes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="banner info" role="status">
      <div class="banner-text">
        <strong>
          {{ imported().name || 'The service' }} was imported from {{ imported().file }}.
        </strong>
        @for (group of groups(); track group.title) {
          <h5>{{ group.title }}</h5>
          <ul class="problems">
            @for (note of group.notes; track $index) {
              <li>
                @if (note.field) {
                  <code>{{ note.field }}</code
                  >:
                }
                {{ note.message }}
              </li>
            }
          </ul>
        } @empty {
          Every setting of the file was imported.
        }
      </div>
      <button type="button" class="btn btn-link" (click)="closed.emit()">Dismiss</button>
    </div>
  `,
  styles: `
    .banner {
      align-items: flex-start;
      color: var(--dso-navy);
    }
    h5 {
      margin: 6px 0 2px;
      font-size: 12px;
      font-weight: 600;
    }
    .problems {
      font-size: 12px;
    }
  `,
})
export class ImportNotes {
  readonly imported = input.required<ImportedConfig>();
  readonly closed = output();

  protected readonly groups = computed(() => noteGroups(this.imported().notes));
}
