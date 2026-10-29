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
import { BuildTool, DeployTarget, ServiceRequest } from '../core/models';
import { formRevision } from '@common/shared/fields';
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
  followToolDefaults,
  nexusIqApplicationForm,
  prefillNewService,
  sectionInvalid,
  toServiceRequest,
  visibleSectionsForPipeline,
} from '../products/product-form-model';
import { ServiceFields } from '../products/service-fields';
import { buildDefaults } from '../shared/service-template';
import {
  WizardDefaults,
  WizardPipeline,
  WizardService,
  deploys,
  imageRepository,
  namedDefaults,
  openShiftTargets,
  pipelineLabel,
} from './self-service-model';

const NEXUS_IQ_APPLICATION = 'nexusIqApplications.0.application';
const FILLED_FROM_NAME = ['scm.repositoryUrl', 'deployment.appName', NEXUS_IQ_APPLICATION];

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
  imports: [ReactiveFormsModule, DIALOG, FORM_FIELD, ConfigImport, ImportNotes, ServiceFields],
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
          Complete these sections first: {{ missing().join(', ') }}.
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
  private readonly fieldsComponent = viewChild(ServiceFields);

  protected readonly sources = this.data.sources ?? [];
  protected readonly copyFrom = new FormControl<CopySource | null>(null);
  protected readonly imported = signal<ImportedConfig | null>(null);
  protected readonly serviceForm = signal<ServiceForm>(this.initForm());
  private readonly changes = formRevision(this.serviceForm);

  protected readonly pipelineLabel = computed(() => pipelineLabel(this.data.pipeline));
  protected readonly title = computed(() =>
    this.data.service ? `Change ${this.data.service.name}` : 'Add a service',
  );
  protected readonly submitLabel = computed(() =>
    this.data.service ? 'Save service' : 'Add service',
  );
  protected readonly missing = computed(() => {
    this.changes();
    const form = this.serviceForm();
    return visibleSectionsForPipeline(form, this.data.pipeline)
      .filter((section) => sectionInvalid(form, section))
      .map((section) => section.label);
  });

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
      let named = copiedFrom;
      const follow = (name: string) => {
        const next = name.trim();
        if (next) {
          this.followName(form, named, next);
          named = next;
        }
      };
      form.controls.name.valueChanges.subscribe(follow);
      follow(form.controls.name.value);
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

  /**
   * Sets what a new service takes from its name: an empty repository URL, application name or Nexus IQ application,
   * and every value that still holds what the previous name gave, such as the image repositories of a copy.
   */
  private followName(form: ServiceForm, previous: string, name: string): void {
    const before = this.fromName(form, previous);
    const after = this.fromName(form, name);
    const application = after[NEXUS_IQ_APPLICATION];
    if (application) {
      const { scanPattern } = buildDefaults(
        this.data.defaults.template,
        form.controls.build.controls.tool.value,
      );
      const apps = form.controls.nexusIqApplications;
      if (!apps.length) {
        apps.push(nexusIqApplicationForm(application, scanPattern));
      } else if (!apps.at(0).controls.scanPatterns.value && scanPattern) {
        apps.at(0).controls.scanPatterns.setValue(scanPattern);
      }
    }
    for (const [path, value] of Object.entries(after)) {
      const control = form.get(path);
      const current = String(control?.value ?? '').trim();
      if (
        control &&
        value &&
        (current === before[path] || (!current && FILLED_FROM_NAME.includes(path)))
      ) {
        control.setValue(value);
      }
    }
  }

  private fromName(form: ServiceForm, name: string): Record<string, string> {
    if (!name) {
      return {};
    }
    const named = namedDefaults(this.data.defaults, name);
    const values = {
      'scm.repositoryUrl': named.repositoryUrl,
      [NEXUS_IQ_APPLICATION]: named.nexusIqApplication,
    };
    if (form.controls.deployment.controls.target.value !== 'OPENSHIFT') {
      return values;
    }
    const rd = form.controls.openShiftTargets.controls.RD.controls;
    const project = rd.projectBuild.value.trim().replace(/-build$/, '');
    const image = imageRepository(project, name, this.data.defaults.template);
    return {
      ...values,
      'deployment.appName': name,
      ...(project && {
        'openShiftTargets.RD.dockerRepoPush': image,
        'openShiftTargets.RD.dockerRepoPull': image,
        'openShiftTargets.QC.dockerRepoPull': image,
      }),
    };
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
      serviceRequest: req,
    });
  }
}
