import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { AbstractControl, ReactiveFormsModule } from '@angular/forms';
import { pairwise, startWith } from 'rxjs';
import { BuildTool, DeployTarget } from '../core/models';
import { DIALOG } from '@common/ui/dialog';
import { revalidateAll } from '@common/shared/form-controls';
import {
  ServiceForm,
  createNexusIqApplicationForm,
  createServiceForm,
  firstInvalidSection,
  toServiceRequest,
} from '../products/product-form-model';
import { ServiceFields } from '../products/service-fields';
import { buildDefaults } from '../shared/service-template';
import {
  WizardDefaults,
  WizardPipeline,
  WizardService,
  namedDefaults,
  openShiftTargets,
  pipelineLabel,
} from './self-service-model';

export interface ServiceDialogData {
  pipeline: WizardPipeline;
  service: WizardService | null;
  takenNames: readonly string[];
  defaults: WizardDefaults;
}

@Component({
  selector: 'dso-service-dialog',
  imports: [ReactiveFormsModule, DIALOG, ServiceFields],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <div class="header-titles">
        <h2 dsoDialogTitle>{{ title() }}</h2>
        <span class="pipeline-pill">{{ pipelineLabel() }} pipeline</span>
      </div>
    </div>
    <div class="modal-body">
      <dso-service-fields
        #fields
        [form]="serviceForm"
        [pipeline]="data.pipeline"
        [productCode]="data.defaults.productCode"
        [submitted]="checked()"
      />
    </div>
    <div class="modal-footer">
      <button type="button" class="btn btn-link" dsoDialogClose>Cancel</button>
      <button type="button" class="btn btn-primary" (click)="save()">{{ submitLabel() }}</button>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .header-titles {
      display: flex;
      align-items: center;
      gap: 12px;

      h2 {
        margin: 0;
        font-size: 18px;
        font-weight: 600;
      }
    }

    .pipeline-pill {
      background: var(--dso-info-bg, #e8f0fe);
      color: var(--dso-navy, #002d62);
      padding: 3px 8px;
      border-radius: 12px;
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .modal-body {
      width: min(980px, 92vw);
      max-height: calc(85vh - 120px);
      overflow-y: auto;
      padding: 12px 16px;
    }
  `,
})
export class ServiceDialog {
  protected readonly data = inject<ServiceDialogData>(DIALOG_DATA);
  private readonly dialogRef = inject<DialogRef<WizardService, ServiceDialog>>(DialogRef);
  private readonly fieldsComponent = viewChild(ServiceFields);

  protected readonly checked = signal(false);
  protected readonly serviceForm: ServiceForm = this.initForm();

  protected readonly pipelineLabel = computed(() => pipelineLabel(this.data.pipeline));
  protected readonly title = computed(() =>
    this.data.service ? `Change ${this.data.service.name}` : 'Add a service',
  );
  protected readonly submitLabel = computed(() =>
    this.data.service ? 'Save service' : 'Add service',
  );

  constructor() {
    this.serviceForm.controls.name.addValidators((control: AbstractControl) => {
      const name = String(control.value ?? '').trim().toLowerCase();
      return this.data.takenNames.some((taken) => taken.toLowerCase() === name)
        ? { rule: 'Another service of this product already has this name' }
        : null;
    });

    const template = this.data.defaults.template;
    const { tool, buildPath, command } = this.serviceForm.controls.build.controls;
    tool.valueChanges.pipe(startWith(tool.value), pairwise()).subscribe(([previous, next]) => {
      const before = buildDefaults(template, previous);
      const after = buildDefaults(template, next);
      if (!buildPath.value || buildPath.value === before.artifact) {
        buildPath.setValue(after.artifact);
      }
      if (!command.controls.tasks.value || command.controls.tasks.value === before.tasks) {
        command.controls.tasks.setValue(after.tasks);
      }
    });

    if (!this.data.service) {
      this.serviceForm.controls.name.valueChanges.subscribe((name) => {
        const trimmed = (name ?? '').trim();
        if (trimmed) {
          this.applyNamedDefaults(trimmed);
        }
      });
    }
  }

  private initForm(): ServiceForm {
    const s = this.data.service;
    const def = this.data.defaults;
    const tool: BuildTool = s?.tool ?? def.tool ?? 'GRADLE';
    const target: DeployTarget = s?.target ?? def.target ?? 'VM';
    const build = buildDefaults(def.template, tool);

    if (s?.serviceRequest) {
      return createServiceForm(s.serviceRequest);
    }

    const openShift = target === 'OPENSHIFT';
    const initialTargets = openShift && s?.openShiftProject
      ? openShiftTargets(s.openShiftProject, s?.name ?? '', tool, def.template)
      : undefined;

    const form = createServiceForm();
    form.patchValue({
      id: s?.id ?? null,
      name: s?.name ?? '',
      description: s?.description ?? '',
      appScan: { applicationId: s?.appScanId ?? '' },
      build: {
        tool,
        autoSetup: true,
        sourceDir: '.',
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
        createNexusIqApplicationForm({
          application: s.nexusIqApplication,
          scanPatterns: build.scanPattern ? [build.scanPattern] : [],
          stage: 'build',
          failOnNetworkError: false,
        }),
      );
    }

    return form;
  }

  private applyNamedDefaults(serviceName: string): void {
    const named = namedDefaults(this.data.defaults, serviceName);
    const form = this.serviceForm;
    const scm = form.controls.scm.controls;

    if (!scm.repositoryUrl.value && named.repositoryUrl) {
      scm.repositoryUrl.setValue(named.repositoryUrl);
    }

    if (named.nexusIqApplication) {
      const tool = form.controls.build.controls.tool.value;
      const build = buildDefaults(this.data.defaults.template, tool);
      const apps = form.controls.nexusIqApplications;
      if (apps.length === 0) {
        apps.push(
          createNexusIqApplicationForm({
            application: named.nexusIqApplication,
            scanPatterns: build.scanPattern ? [build.scanPattern] : [],
            stage: 'build',
            failOnNetworkError: false,
          }),
        );
      } else {
        if (!apps.controls[0].controls.application.value) {
          apps.controls[0].controls.application.setValue(named.nexusIqApplication);
        }
        if (!apps.controls[0].controls.scanPatterns.value && build.scanPattern) {
          apps.controls[0].controls.scanPatterns.setValue(build.scanPattern);
        }
      }
    }
  }

  protected save(): void {
    this.checked.set(true);
    revalidateAll(this.serviceForm);

    const invalidSection = firstInvalidSection(this.serviceForm, this.data.pipeline);
    if (invalidSection) {
      this.fieldsComponent()?.revealFirstProblem();
      return;
    }

    const req = toServiceRequest(this.serviceForm);
    const target = req.deployment.target;
    const openShift = target === 'OPENSHIFT';
    const openShiftProject = openShift
      ? req.openShiftTargets?.RD?.projectBuild?.replace(/-build$/, '') ?? ''
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
