import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { AbstractControl, FormControl, ReactiveFormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { BuildTool, DeployTarget, ServiceRequest } from '../core/models';
import { revalidateAll } from '@common/shared/form-controls';
import { DIALOG } from '@common/ui/dialog';
import { FORM_FIELD } from '@common/ui/form-field';
import {
  ConfigImport,
  ImportNotes,
  ImportedConfig,
  importedServiceForm,
  prefillAgents,
} from '../products/config-import';
import {
  ServiceForm,
  copiedService,
  createServiceForm,
  dvApplication,
  firstInvalidSection,
  followName,
  followToolDefaults,
  nexusIqApplicationForm,
  prefillNewService,
  toServiceRequest,
} from '../products/product-form-model';
import { MissingFields } from '../products/missing-fields';
import { ServiceFields } from '../products/service-fields';
import { buildDefaults } from '../shared/service-template';
import {
  WizardDefaults,
  WizardPipeline,
  WizardService,
  nodeDirectoryOf,
  deploys,
  openShiftTargets,
  pipelineLabel,
} from './self-service-model';

export interface CopySource {
  name: string;
  request: Partial<ServiceRequest>;
}

export interface ServiceDialogData {
  pipeline: WizardPipeline;
  service: WizardService | null;
  takenNames: readonly string[];
  defaults: WizardDefaults;
  sources?: readonly CopySource[];
  agents?: FormControl<string>;
}

@Component({
  selector: 'dso-service-dialog',
  imports: [
    ReactiveFormsModule,
    DIALOG,
    FORM_FIELD,
    ConfigImport,
    ImportNotes,
    MissingFields,
    ServiceFields,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 dsoDialogTitle>{{ title() }}</h2>
    </div>
    <div class="modal-body">
      <p class="subtitle">
        <span class="tag">{{ pipelineLabel() }} pipeline</span>
      </p>
      @if (!data.service) {
        <div class="start">
          @if (sources.length) {
            <dso-form-field
              help="Replaces the settings below with those of another service of the product. What identifies that service is not copied: the SonarQube project, monitoring project tag and SAST scan name stay empty, and the OpenShift application name, image repositories and Nexus IQ application are set from the name typed here."
            >
              <dso-label>Copy values from</dso-label>
              <select dsoInput [formControl]="copyFrom">
                <option [ngValue]="null">No service</option>
                @for (source of sources; track $index) {
                  <option [ngValue]="source">{{ source.name }}</option>
                }
              </select>
            </dso-form-field>
          }
          <dso-config-import
            [serviceName]="serviceForm().controls.name.value"
            (imported)="importConfig($event)"
          />
        </div>
        @if (imported(); as notes) {
          <dso-import-notes [imported]="notes" (closed)="imported.set(null)" />
        }
      }
      <dso-service-fields
        #fields
        [form]="serviceForm()"
        [pipeline]="data.pipeline"
        [productCode]="data.defaults.productCode"
        [defaults]="data.defaults.settings"
        [submitted]="true"
      />
    </div>
    <div class="modal-footer">
      @if (missing().length) {
        <p class="missing" role="status">
          Complete these fields first:
          <dso-missing-fields [missing]="missing()" (open)="fieldsComponent()?.reveal($event)" />
          <span>.</span>
        </p>
      }
      <button type="button" class="btn btn-link" dsoDialogClose>Cancel</button>
      <button
        type="button"
        class="btn btn-primary"
        [disabled]="missing().length > 0"
        (click)="save()"
      >
        {{ submitLabel() }}
      </button>
    </div>
  `,
  styles: `
    :host {
      display: block;
      width: min(980px, 92vw);
    }
    .start {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      gap: 8px 16px;
      margin-bottom: 10px;

      dso-form-field {
        flex: 0 1 320px;
      }
    }
    .missing {
      flex: 1;
      margin: 0;
      color: var(--dso-danger);
      font-size: 12px;
    }
  `,
})
export class ServiceDialog {
  protected readonly data = inject<ServiceDialogData>(DIALOG_DATA);
  private readonly dialogRef = inject<DialogRef<WizardService, ServiceDialog>>(DialogRef);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly fieldsComponent = viewChild(ServiceFields);

  protected readonly sources = this.data.sources ?? [];
  protected readonly copyFrom = new FormControl<CopySource | null>(null);
  protected readonly imported = signal<ImportedConfig | null>(null);
  protected readonly serviceForm = signal<ServiceForm>(this.initForm());

  protected readonly pipelineLabel = computed(() => pipelineLabel(this.data.pipeline));
  protected readonly title = computed(() =>
    this.data.service ? `Change ${this.data.service.name}` : 'Add a service',
  );
  protected readonly submitLabel = computed(() =>
    this.data.service ? 'Save service' : 'Add service',
  );
  protected readonly missing = computed(() => this.fieldsComponent()?.missing() ?? []);

  constructor() {
    this.use(this.serviceForm());
    this.copyFrom.valueChanges.subscribe((source) => {
      this.imported.set(null);
      this.replace(
        source ? createServiceForm(copiedService(source.request)) : this.initForm(),
        source?.name,
      );
    });
  }

  private initForm(): ServiceForm {
    const s = this.data.service;
    const def = this.data.defaults;
    const tool: BuildTool = s?.tool ?? def.tool ?? 'GRADLE';
    const target: DeployTarget =
      s?.target ?? (deploys(this.data.pipeline) ? def.target : null) ?? 'VM';
    const build = buildDefaults(def.template, tool);

    if (s?.serviceRequest) {
      return createServiceForm(s.serviceRequest);
    }

    const openShift = target === 'OPENSHIFT';
    const initialTargets =
      openShift && s?.openShiftProject
        ? openShiftTargets(s.openShiftProject, s?.name ?? '', tool, def.template)
        : undefined;

    const form = createServiceForm(undefined, def.settings?.serviceDefaults);
    form.patchValue({
      id: s?.id ?? null,
      name: s?.name ?? '',
      description: s?.description ?? '',
      appScan: { applicationId: s?.appScanId ?? '' },
      build: {
        tool,
        autoSetup: true,
        buildPath: build.artifact,
        command: { tasks: build.tasks },
      },
      delivery: {
        tasks: def.template?.deliveryTasks ?? '',
      },
      deployment: {
        target,
        appName: openShift ? (s?.name ?? '') : '',
        artifactName: openShift ? `${s?.name ?? 'service'}.jar` : '',
      },
      scm: {
        repositoryUrl: s?.repositoryUrl ?? '',
        credentialsId: def.template?.bitbucketCredentialsId ?? '',
      },
    });

    if (initialTargets) {
      form.patchValue({ openShiftTargets: initialTargets as any });
    }

    if (s?.nexusIqApplication) {
      form.controls.nexusIqApplications.clear();
      form.controls.nexusIqApplications.push(
        nexusIqApplicationForm(s.nexusIqApplication, build.scanPattern),
      );
    }

    return form;
  }

  /** Sets up a form; a copy's values that the source took from its name follow the name typed here instead. */
  private use(form: ServiceForm, copiedFrom = ''): void {
    form.controls.name.addValidators((control: AbstractControl) => {
      const name = String(control.value ?? '')
        .trim()
        .toLowerCase();
      return this.data.takenNames.some((taken) => taken.toLowerCase() === name)
        ? { rule: 'Another service of this product already has this name' }
        : null;
    });
    form.controls.name.updateValueAndValidity();

    if (this.data.pipeline === 'FULL') {
      form.controls.urbanCodeApplications.addValidators(dvApplication);
      form.controls.urbanCodeApplications.updateValueAndValidity();
    }

    followToolDefaults(form, this.data.defaults.template, this.destroyRef);

    if ((this.data.service?.id ?? null) === null) {
      prefillNewService(
        form,
        this.data.defaults.settings,
        this.sources.map((source) => source.request.appScan ?? {}),
      );
    }
    if (!this.data.service) {
      const { template, productCode } = this.data.defaults;
      followName(form, template, of(productCode), this.destroyRef, copiedFrom);
    }
  }

  private replace(form: ServiceForm, copiedFrom?: string): void {
    form.controls.name.setValue(this.serviceForm().controls.name.value);
    this.use(form, copiedFrom);
    this.serviceForm.set(form);
  }

  protected importConfig(imported: ImportedConfig): void {
    const form = importedServiceForm(
      imported,
      this.data.defaults.settings?.serviceDefaults,
      this.serviceForm().controls.name.value,
    );
    this.use(form);
    this.serviceForm.set(form);
    this.copyFrom.setValue(null, { emitEvent: false });
    prefillAgents(this.data.agents, imported.agentLabels);
    this.imported.set(imported);
  }

  protected save(): void {
    const form = this.serviceForm();
    revalidateAll(form);

    const invalidSection = firstInvalidSection(form, this.data.pipeline);
    if (invalidSection) {
      this.fieldsComponent()?.revealFirstProblem();
      return;
    }

    const req = toServiceRequest(form);
    const target = req.deployment.target;
    const openShift = target === 'OPENSHIFT';
    const openShiftProject = openShift
      ? (req.openShiftTargets?.RD?.projectBuild?.replace(/-build$/, '') ?? '')
      : '';
    const nexusIqApp = req.nexusIqApplications?.[0]?.application ?? '';
    const repositoryUrl = req.scm?.repositoryUrl ?? '';

    this.dialogRef.close({
      id: req.id,
      name: req.name,
      description: req.description ?? '',
      appScanId: req.appScan.applicationId,
      tool: req.build.tool,
      target,
      openShiftProject,
      nexusIqApplication: nexusIqApp,
      repositoryUrl,
      nodeDirectory: nodeDirectoryOf(req.nodeBuild),
      serviceRequest: req,
    });
  }
}
