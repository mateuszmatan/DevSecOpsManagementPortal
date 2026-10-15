import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import {
  AbstractControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { BuildTool, DeployTarget } from '../core/models';
import { SERVICE_NAME, UUID } from '../products/product-form-model';
import { HTTP_URL_ERROR, filled, max, text, url } from '@common/shared/form-controls';
import { errorText } from '@common/shared/form-errors';
import { ChoiceTiles } from '../shared/choice-tiles';
import { DIALOG } from '@common/ui/dialog';
import { FORM_FIELD } from '@common/ui/form-field';
import {
  NamedDefaults,
  OPENSHIFT_PROJECT,
  TARGETS,
  TOOLS,
  WizardDefaults,
  WizardPipeline,
  WizardService,
  deploys,
  namedDefaults,
  toolName,
} from './self-service-model';

export interface ServiceDialogData {
  pipeline: WizardPipeline;
  service: WizardService | null;
  takenNames: readonly string[];
  defaults: WizardDefaults;
}

const NAME_HELP = "Use letters, digits, '.', '-' or '_', starting with a letter or digit";
const APP_SCAN_HELP = 'Paste the ID as it is, for example 109f44ac-cc06-4ca0-884e-d944904f7019';
const PROJECT_HELP = "Use lower case letters, digits and '-', for example pay-payhub";
const PARTS = ['About the service', 'Build and run'];

@Component({
  selector: 'dso-service-dialog',
  imports: [ReactiveFormsModule, DIALOG, FORM_FIELD, ChoiceTiles],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 dsoDialogTitle>{{ data.service ? 'Change ' + data.service.name : 'Add a service' }}</h2>
    </div>
    <form [formGroup]="form" (ngSubmit)="next()" novalidate>
      <div class="modal-body">
        <p class="page-count">Part {{ page() }} of 2 · {{ parts[page() - 1] }}</p>
        @if (page() === 1) {
          <p class="help">Fields marked * are required.</p>
          <dso-form-field class="full-width">
            <dso-label>Service name</dso-label>
            <input
              dsoInput
              formControlName="name"
              placeholder="backend-api"
              autocomplete="off"
              required
            />
            <dso-hint
              >A short name without spaces, for example web or backend-api; it becomes part of the
              Jenkins job name</dso-hint
            >
            <dso-error>{{ errorText(form.controls.name, nameHelp) }}</dso-error>
          </dso-form-field>
          <dso-form-field class="full-width">
            <dso-label>What it does</dso-label>
            <input dsoInput formControlName="description" placeholder="REST API of the product" />
            <dso-hint>A few plain words for people who do not know the service</dso-hint>
            <dso-error>{{ errorText(form.controls.description) }}</dso-error>
          </dso-form-field>
          <dso-form-field class="full-width">
            <dso-label>AppScan application ID</dso-label>
            <input
              dsoInput
              class="mono"
              formControlName="appScanId"
              placeholder="109f44ac-cc06-4ca0-884e-d944904f7019"
              autocomplete="off"
              required
            />
            <dso-hint
              >The ID of this service in HCL AppScan, the security scanner. The Application Security
              team gives it to you.</dso-hint
            >
            <dso-error>{{ errorText(form.controls.appScanId, appScanHelp) }}</dso-error>
          </dso-form-field>
        } @else {
          <p class="help">If you are not sure about an answer, ask a developer of the service.</p>
          <h3>What builds the code?</h3>
          <dso-choice-tiles label="Build tool" [options]="tools" [(value)]="tool" />
          @if (otherTool) {
            <p class="help">
              It is built with {{ otherTool }} today. Choose Gradle or Maven only if that changes.
            </p>
          }
          @if (checked() && tool() === null) {
            <p class="choice-error">Choose Gradle or Maven</p>
          }
          @if (deploys) {
            <h3>Where does it run?</h3>
            <dso-choice-tiles label="Runs on" [options]="targets" [(value)]="target" />
            @if (checked() && target() === null) {
              <p class="choice-error">Choose where the service runs</p>
            }
            @if (needsProject()) {
              <dso-form-field class="full-width project">
                <dso-label>OpenShift project</dso-label>
                <input
                  dsoInput
                  class="mono"
                  formControlName="openShiftProject"
                  placeholder="pay-payhub"
                  autocomplete="off"
                />
                <dso-hint
                  >Where the service lives on OpenShift: its project name without -rd or -qc at the
                  end</dso-hint
                >
                <dso-error>{{ errorText(form.controls.openShiftProject, projectHelp) }}</dso-error>
              </dso-form-field>
            }
          }
          @if (scans) {
            <h3>Nexus IQ and Bitbucket</h3>
            <p class="help">
              Nexus IQ checks the open-source libraries the service uses, and GoldenFix proposes
              safe versions in its Bitbucket repository.
            </p>
            <dso-form-field class="full-width">
              <dso-label>Nexus IQ application</dso-label>
              <input
                dsoInput
                class="mono"
                formControlName="nexusIqApplication"
                placeholder="cert-scanner-gui"
                autocomplete="off"
                required
              />
              <dso-hint>The name of the service in Nexus IQ, for example payhub-gateway</dso-hint>
              <dso-error>{{ errorText(form.controls.nexusIqApplication) }}</dso-error>
            </dso-form-field>
            <dso-form-field class="full-width">
              <dso-label>Bitbucket repository</dso-label>
              <input
                dsoInput
                formControlName="repositoryUrl"
                placeholder="https://bitbucket.bbh.com/projects/TA/repos/cert-scanner"
                autocomplete="off"
                required
              />
              <dso-hint
                >The web address of the service's code in Bitbucket; GoldenFix opens its pull
                requests there</dso-hint
              >
              <dso-error>{{ errorText(form.controls.repositoryUrl, urlHelp) }}</dso-error>
            </dso-form-field>
          }
          @if (existing) {
            <p class="note">
              {{
                deploys
                  ? 'If you change how it is built or where it runs, its build or deployment settings go back to the BBH defaults.'
                  : 'If you change how it is built, its build settings go back to the BBH defaults.'
              }}
            </p>
          }
        }
      </div>
      <div class="modal-footer">
        @if (page() === 2) {
          <button type="button" class="btn btn-link" (click)="page.set(1)">Back</button>
        } @else {
          <button type="button" class="btn btn-link" dsoDialogClose>Cancel</button>
        }
        <button type="submit" class="btn btn-primary">{{ submitLabel() }}</button>
      </div>
    </form>
  `,
  styles: `
    .modal-body {
      display: flex;
      flex-direction: column;
      gap: 6px;
      width: min(560px, 80vw);
    }

    .page-count {
      margin: 0 0 6px;
      color: var(--dso-muted);
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    h3 {
      margin: 4px 0 2px;
      font-size: 14px;
    }

    .project {
      margin-top: 10px;
    }

    .note,
    .help {
      margin: 0 0 4px;
      color: var(--dso-muted);
      font-size: 12px;
    }

    .choice-error {
      margin-top: 2px;
    }
  `,
})
export class ServiceDialog {
  protected readonly data = inject<ServiceDialogData>(DIALOG_DATA);
  private readonly dialogRef = inject<DialogRef<WizardService, ServiceDialog>>(DialogRef);

  private readonly start = this.data.service;
  protected readonly existing = this.start?.id != null;
  protected readonly deploys = deploys(this.data.pipeline);
  protected readonly scans = this.data.pipeline === 'NEXUS_IQ';
  private readonly onOpenShift =
    this.existing && this.start?.target === 'OPENSHIFT' && !this.start.openShiftProject;
  protected readonly tools = TOOLS;
  protected readonly otherTool =
    this.start && !TOOLS.some((option) => option.value === this.start!.tool)
      ? toolName(this.start.tool)
      : null;
  protected readonly targets = TARGETS;
  protected readonly nameHelp = NAME_HELP;
  protected readonly appScanHelp = APP_SCAN_HELP;
  protected readonly projectHelp = PROJECT_HELP;
  protected readonly urlHelp = HTTP_URL_ERROR;
  protected readonly errorText = errorText;

  protected readonly page = signal<1 | 2>(1);
  protected readonly checked = signal(false);
  protected readonly tool = signal<BuildTool | null>(
    this.start?.tool ??
      (TOOLS.some((option) => option.value === this.data.defaults.tool)
        ? this.data.defaults.tool
        : null),
  );
  protected readonly target = signal<DeployTarget | null>(
    this.start?.target ?? this.data.defaults.target,
  );
  protected readonly needsProject = computed(
    () => this.deploys && this.target() === 'OPENSHIFT' && !this.onOpenShift,
  );
  protected readonly parts = PARTS;
  protected readonly submitLabel = computed(() =>
    this.page() === 1 ? `Next: ${PARTS[1]}` : this.start ? 'Save service' : 'Add service',
  );

  protected readonly form = new FormGroup({
    name: text(
      this.start?.name,
      filled,
      Validators.pattern(SERVICE_NAME),
      max(100),
      (control: AbstractControl) => this.unique(control),
    ),
    description: text(this.start?.description, max(2000)),
    appScanId: text(this.start?.appScanId, filled, Validators.pattern(UUID)),
    openShiftProject: text(this.start?.openShiftProject, Validators.pattern(OPENSHIFT_PROJECT)),
    nexusIqApplication: text(this.start?.nexusIqApplication, filled, max(200)),
    repositoryUrl: url(this.start?.repositoryUrl, 1000, filled),
  });

  protected next(): void {
    const { name, description, appScanId, openShiftProject, nexusIqApplication, repositoryUrl } =
      this.form.controls;
    if (this.page() === 1) {
      [name, description, appScanId].forEach((control) => control.markAsTouched());
      if (name.valid && description.valid && appScanId.valid) {
        this.prefill(name.value.trim());
        this.page.set(2);
      }
      return;
    }
    this.checked.set(true);
    [openShiftProject, nexusIqApplication, repositoryUrl].forEach((control) =>
      control.markAsTouched(),
    );
    if (this.tool() === null || (this.deploys && this.target() === null)) {
      return;
    }
    if (this.needsProject() && !openShiftProject.value.trim()) {
      openShiftProject.setErrors({ required: true });
    }
    const placed = !this.needsProject() || openShiftProject.valid;
    const linked = !this.scans || (nexusIqApplication.valid && repositoryUrl.valid);
    if (placed && linked) {
      this.finish();
    }
  }

  private prefilled: NamedDefaults = {
    openShiftProject: '',
    nexusIqApplication: '',
    repositoryUrl: '',
  };

  private prefill(serviceName: string): void {
    const named = namedDefaults(this.data.defaults, serviceName);
    for (const key of Object.keys(named) as (keyof NamedDefaults)[]) {
      const control = this.form.controls[key];
      if (!control.value.trim() || control.value === this.prefilled[key]) {
        control.setValue(named[key]);
      }
    }
    this.prefilled = named;
  }

  private finish(): void {
    const value = this.form.getRawValue();
    this.dialogRef.close({
      id: this.start?.id ?? null,
      name: value.name.trim(),
      description: value.description.trim(),
      appScanId: value.appScanId.trim().toLowerCase(),
      tool: this.tool()!,
      target: this.target(),
      openShiftProject: this.needsProject() ? value.openShiftProject.trim() : '',
      nexusIqApplication: value.nexusIqApplication.trim(),
      repositoryUrl: value.repositoryUrl.trim(),
    });
  }

  private unique(control: AbstractControl): ValidationErrors | null {
    const name = String(control.value ?? '')
      .trim()
      .toLowerCase();
    return this.data.takenNames.some((taken) => taken.toLowerCase() === name)
      ? { rule: 'Another service of this product already has this name' }
      : null;
  }
}
