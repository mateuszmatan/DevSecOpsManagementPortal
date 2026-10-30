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
import { FormControl } from '@angular/forms';
import { Observable, finalize, from, map, of, switchMap, throwError } from 'rxjs';
import { ProductsApi } from '../core/api';
import { ImportedService, ServiceDefaults, ServiceRequest } from '../core/models';
import { errorMessage, fieldProblems } from '@common/core/errors';
import { FieldProblem } from '@common/core/models';
import { Notifier } from '@common/core/notifier';
import { joinWords } from '@common/shared/form-controls';
import { capitalized } from '@common/shared/formatting';
import { DIALOG } from '@common/ui/dialog';
import { DsoHelp } from '@common/ui/help';
import { CONFIG_FILE, CONFIG_FILE_NAMES, configFileOf } from '../shared/config-file';
import { ServiceForm, createServiceForm } from './product-form-model';

export const MAX_CONFIG_BYTES = 262_144;

export interface ImportedConfig {
  file: string;
  name: string;
  service: Partial<ServiceRequest>;
  notes: FieldProblem[];
  agentLabels: string[] | null;
}

const NOT_IMPORTED = /^not imported\b/i;
const PREFIX = /^(not imported|check)\b[:,]?\s*/i;

export function noteGroups(notes: readonly FieldProblem[]) {
  const group = (title: string, imported: boolean) => ({
    title,
    notes: notes
      .filter((note) => NOT_IMPORTED.test(note.message) !== imported)
      .map((note) => ({ ...note, message: capitalized(note.message.replace(PREFIX, '')) })),
  });
  return [group('Not imported', false), group('Check', true)].filter(
    (candidate) => candidate.notes.length,
  );
}

/** The new service form an import fills, named as typed or, when nothing is typed, as in the file. */
export function importedServiceForm(
  imported: ImportedConfig,
  defaults: ServiceDefaults | null | undefined,
  typedName = '',
): ServiceForm {
  const form = createServiceForm({ ...imported.service, id: null }, defaults);
  form.controls.name.setValue(typedName.trim() || imported.name);
  return form;
}

/** Fills the Jenkins agents with the labels of the imported pipeline while the user has not changed them. */
export function prefillAgents(
  agents: FormControl<string> | undefined,
  labels: readonly string[] | null,
): void {
  if (agents?.enabled && !agents.dirty && labels?.length) {
    agents.setValue(joinWords(labels, ', '));
  }
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

/** The note that names the chosen files an import did not read. */
const skippedNotes = (file: File, skipped: readonly File[]): FieldProblem[] =>
  skipped.length
    ? [
        {
          field: '',
          message:
            `check: the import did not read ${skipped.map((f) => f.name).join(', ')}` +
            (file.name === CONFIG_FILE && skipped.some((f) => f.name === CONFIG_FILE_NAMES[1])
              ? `: the DevSecOps library reads ${CONFIG_FILE} when a repository has both`
              : ''),
        },
      ]
    : [];

@Component({
  selector: 'dso-config-import',
  imports: [DsoHelp],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'dso-config-import' },
  template: `
    <button
      type="button"
      class="btn btn-outline-primary"
      [disabled]="disabled() || busy()"
      (click)="file.click()"
    >
      Import devsecops_config.yaml
    </button>
    <dso-help
      label="Import devsecops_config.yaml"
      text="Reads a devsecops_config.yaml, a config.yaml (its former name), a configuration the portal rendered or its JSON form. Choose several files, for example both files of a repository, and the import reads devsecops_config.yaml, else config.yaml, as the DevSecOps library does."
    />
    <input
      #file
      type="file"
      accept=".yaml,.yml,.json"
      multiple
      hidden
      aria-label="devsecops_config.yaml or config.yaml to import"
      (change)="read(file)"
    />
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
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
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (!files.length) {
      return;
    }
    const choice = configFileOf(files);
    if (!choice) {
      this.notifier.error(
        'None of the chosen files is named devsecops_config.yaml or config.yaml. ' +
          'Choose one of them, or a single file of any name.',
      );
      return;
    }
    const { file, skipped } = choice;
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
            notes: [...skippedNotes(file, skipped), ...picked.result.notes, ...picked.chosen.notes],
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
