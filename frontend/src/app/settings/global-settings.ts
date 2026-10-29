import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  OnInit,
  afterNextRender,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { Dialog } from '@angular/cdk/dialog';
import { ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { SettingsApi } from '../core/api';
import { GlobalSettings, SCANNERS, Scanner } from '../core/models';
import { Notifier } from '@common/core/notifier';
import { HasUnsavedChanges } from '@common/core/unsaved-changes';
import { GoldenFixFields } from '../products/golden-fix-fields';
import { showCode } from '../shared/code-dialog';
import { Fields, chips } from '@common/shared/fields';
import { revalidateAll, saveFailure } from '@common/shared/form-controls';
import { errorText } from '@common/shared/form-errors';
import { RelativeTimePipe } from '@common/shared/formatting';
import { DsoHelp } from '@common/ui/help';
import { LoadError } from '@common/ui/load-error';
import { DsoLoading, DsoSpinner } from '@common/ui/loading';
import { TOGGLES } from '@common/ui/toggle-group';
import {
  GOLDEN_FIX_ENABLED,
  LIMIT_FIELDS,
  RELEASE_GATE_FIELDS,
  SETTINGS_PAGE,
} from './settings-fields';
import {
  SettingsSectionId,
  createSettingsForm,
  firstInvalidSection,
  patchSettings,
  toSettingsRequest,
} from './settings-form-model';
import { FieldProblem } from '@common/core/models';

const SCANNER_INFO: Record<
  Scanner,
  { label: string; tool: string; help: string; gateKey: string; path: string }
> = {
  SAST: {
    label: 'SAST',
    tool: 'Scan of the source code by HCL AppScan',
    help: 'Counts the issues of the static scan report by their AppScan severity.',
    gateKey: 'sast',
    path: 'sast',
  },
  SCA: {
    label: 'SCA',
    tool: 'Vulnerability check of the source code by SonarQube',
    help: 'Counts the open SonarQube vulnerabilities of the project: Blocker and Critical as critical, Major as high and Minor as medium.',
    gateKey: 'sca',
    path: 'sca',
  },
  NEXUS_IQ: {
    label: 'Nexus IQ',
    tool: 'Policy check of the open source libraries by Sonatype Nexus IQ',
    help: 'Counts the components with policy violations: critical as critical, severe as high and moderate as medium.',
    gateKey: 'niq',
    path: 'tools.nexusIq',
  },
  DAST: {
    label: 'DAST',
    tool: 'Scan of the running service by HCL AppScan',
    help: 'Counts the issues of the dynamic scan report by their AppScan severity.',
    gateKey: 'dast',
    path: 'dast',
  },
};

@Component({
  selector: 'dso-global-settings',
  imports: [
    ReactiveFormsModule,
    TOGGLES,
    DsoHelp,
    DsoLoading,
    DsoSpinner,
    LoadError,
    Fields,
    GoldenFixFields,
    RelativeTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './global-settings.html',
  styleUrl: './global-settings.scss',
})
export class GlobalSettingsPage implements OnInit, HasUnsavedChanges {
  private readonly api = inject(SettingsApi);
  private readonly notifier = inject(Notifier);
  private readonly dialog = inject(Dialog);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = createSettingsForm();
  protected readonly settings = signal<GlobalSettings | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<unknown>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly conflict = signal(false);
  protected readonly unmatchedProblems = signal<FieldProblem[]>([]);
  protected readonly submitted = signal(false);
  protected readonly loadingConfig = signal(false);
  protected readonly formEvent = toSignal(this.form.events);

  protected readonly errorText = errorText;
  protected readonly scanners = SCANNERS;
  protected readonly scannerInfo = SCANNER_INFO;
  protected readonly limitFields = LIMIT_FIELDS;
  protected readonly releaseGateFields = RELEASE_GATE_FIELDS;
  protected readonly goldenFixEnabled = GOLDEN_FIX_ENABLED;

  ngOnInit(): void {
    this.load();
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty;
  }

  protected sections() {
    this.formEvent();
    return SETTINGS_PAGE.map((section) => {
      const group = this.form.controls[section.id];
      return { ...section, problem: group.invalid && (this.submitted() || group.touched) };
    });
  }

  protected groupOf(id: SettingsSectionId) {
    return this.form.controls[id];
  }

  protected gateHint(): string {
    const keys = SCANNERS.map((scanner) => `\`${SCANNER_INFO[scanner].gateKey}\``).join(', ');
    return chips(`Click a scan to add or remove it · \`releaseGate.scanners\`, written as ${keys}`);
  }

  protected scrollTo(id: SettingsSectionId): void {
    document
      .getElementById(`settings-${id}`)
      ?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
  }

  protected save(): void {
    this.submitted.set(true);
    this.saveError.set(null);
    this.conflict.set(false);
    this.unmatchedProblems.set([]);
    revalidateAll(this.form);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.saveError.set('Some fields need your attention.');
      this.revealProblem();
      return;
    }
    const request = toSettingsRequest(this.form, this.settings()?.version ?? null);
    this.saving.set(true);
    this.api
      .update(request)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (settings) => {
          this.apply(settings);
          this.notifier.success(
            'Library defaults saved. Every pipeline gets them the next time it runs.',
          );
        },
        error: (error) => this.showSaveError(error),
      });
  }

  protected discard(): void {
    const settings = this.settings();
    if (settings) {
      this.apply(settings);
    }
  }

  protected showConfig(): void {
    this.loadingConfig.set(true);
    this.api
      .config()
      .pipe(
        finalize(() => this.loadingConfig.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (code) =>
          showCode(this.dialog, {
            title: 'Shared settings sent to Jenkins (config.yaml)',
            subtitle:
              'The part of config.yaml every pipeline receives along with the settings of its ' +
              'own service, built from the saved library defaults.' +
              (this.form.dirty ? ' Your unsaved changes are not included.' : ''),
            code,
            fileName: 'devsecops-global.yaml',
          }),
        error: (error) => this.notifier.error(error),
      });
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api
      .get()
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (settings) => this.apply(settings),
        error: (error) => this.loadError.set(error),
      });
  }

  private apply(settings: GlobalSettings): void {
    patchSettings(this.form, settings);
    this.settings.set(settings);
    this.submitted.set(false);
    this.saveError.set(null);
    this.conflict.set(false);
    this.unmatchedProblems.set([]);
  }

  private showSaveError(error: unknown): void {
    const failure = saveFailure(
      this.form,
      error,
      'Not saved: the settings were changed by someone else.',
    );
    this.conflict.set(failure.conflict);
    this.saveError.set(failure.message);
    this.unmatchedProblems.set(failure.unmatched);
    if (failure.conflict) {
      afterNextRender(
        () =>
          document
            .querySelector('.banner.conflict')
            ?.scrollIntoView?.({ behavior: 'smooth', block: 'center' }),
        { injector: this.injector },
      );
    } else {
      this.revealProblem();
    }
  }

  private revealProblem(): void {
    const section = firstInvalidSection(this.form);
    afterNextRender(
      () => {
        const target = section && document.getElementById(`settings-${section}`);
        const field =
          target?.querySelector('.dso-form-field.has-error, .field-error') ??
          (this.unmatchedProblems().length ? document.querySelector('.settings .problems') : null);
        (field ?? target)?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
      },
      { injector: this.injector },
    );
  }
}
