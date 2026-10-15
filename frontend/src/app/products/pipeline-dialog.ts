import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { EMPTY, Observable, catchError, finalize, ignoreElements, of, tap, throwError } from 'rxjs';
import { PipelinesApi, ServiceTemplateApi } from '../core/api';
import { errorMessage, fieldProblems } from '@common/core/errors';
import {
  PIPELINE_TYPES,
  Pipeline,
  PipelineRequest,
  PipelineType,
  ServicePipelines,
  ServiceTemplate,
} from '../core/models';
import { KEY_MEANING, pipelineName } from '../pipelines/pipeline-texts';
import { Field, Fields, area, choice, mono } from '@common/shared/fields';
import {
  applyFieldProblems,
  commaItems,
  eachItem,
  filled,
  fitsColumn,
  joinWords,
  max,
  setEnabled,
  text,
} from '@common/shared/form-controls';
import { fillTemplate } from '../shared/service-template';
import { DIALOG } from '@common/ui/dialog';
import { DsoSpinner } from '@common/ui/loading';

export interface PipelineDialogData {
  service: Pick<ServicePipelines, 'serviceId' | 'serviceName' | 'pipelines'>;
  productCode?: string;
  pipeline?: Pipeline;
}

export const AGENT_LABEL = /^.{1,100}$/;
export const JENKINS_JOB = /^(https?:\/\/\S+|[^\s:?#][^:?#]*)$/;
export const JOB_PATH = /^(?!.*\.\.)[A-Za-z0-9._ /-]+$/;
const JOB_PATH_ERROR = "A job path such as DevSecOps/CERT/backend-api-extended, without '..'";
const MAX_LABELS = 20;
const STALE =
  'Not saved: someone else changed the settings of this pipeline after you opened them. ' +
  'The form now shows their settings: make your change again, then save.';

function settingsOf(pipeline: Pipeline | undefined) {
  return {
    agentLabels: joinWords(pipeline?.agentLabels ?? ['linux-agent'], ', '),
    jenkinsJob: pipeline?.jenkinsJob ?? '',
    extendedPipelineJob: pipeline?.extendedPipelineJob ?? '',
    securityPipelineJob: pipeline?.securityPipelineJob ?? '',
    description: pipeline?.description ?? '',
  };
}

@Component({
  selector: 'dso-pipeline-dialog',
  imports: [ReactiveFormsModule, DIALOG, DsoSpinner, Fields],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pipeline-dialog.html',
  styleUrl: './pipeline-dialog.scss',
})
export class PipelineDialog {
  protected readonly data = inject<PipelineDialogData>(DIALOG_DATA);
  private readonly dialogRef = inject<DialogRef<Pipeline, PipelineDialog>>(DialogRef);
  private readonly api = inject(PipelinesApi);

  protected readonly editing = this.data.pipeline !== undefined;
  protected readonly title = this.data.pipeline
    ? `Settings of the pipeline ${pipelineName(this.data.pipeline)}`
    : `Add a pipeline to ${this.data.service.serviceName}`;
  protected readonly keyMeaning = KEY_MEANING;
  protected readonly types = PIPELINE_TYPES.filter(
    (type) =>
      type.value === this.data.pipeline?.type ||
      !this.data.service.pipelines.some((p) => p.type === type.value),
  );

  private readonly stored = settingsOf(this.data.pipeline);
  private version = this.data.pipeline?.version ?? null;

  protected readonly form = new FormGroup({
    type: new FormControl<PipelineType>(
      { value: this.data.pipeline?.type ?? this.types[0]?.value ?? 'FULL', disabled: this.editing },
      { nonNullable: true },
    ),
    agentLabels: text(
      this.stored.agentLabels,
      filled,
      (control) =>
        commaItems(control.value).length > MAX_LABELS ? { maxItems: { max: MAX_LABELS } } : null,
      eachItem(commaItems, AGENT_LABEL, 'At most 100 characters per label'),
      fitsColumn(commaItems, ',', 1000),
    ),
    jenkinsJob: text(this.stored.jenkinsJob, Validators.pattern(JENKINS_JOB), max(1000)),
    extendedPipelineJob: text(
      this.stored.extendedPipelineJob,
      Validators.pattern(JOB_PATH),
      max(500),
    ),
    securityPipelineJob: text(
      this.stored.securityPipelineJob,
      Validators.pattern(JOB_PATH),
      max(500),
    ),
    description: text(this.stored.description, max(1000)),
  });

  private readonly type = toSignal(this.form.controls.type.valueChanges, {
    initialValue: this.form.controls.type.value,
  });
  protected readonly selectedType = computed(() =>
    PIPELINE_TYPES.find((type) => type.value === this.type()),
  );
  protected readonly saving = signal(false);

  protected readonly error = signal<string | null>(null);

  protected readonly fields = computed<Field[]>(() => {
    const type = this.selectedType();
    const job = (key: string, label: string, code: string, example: string, hint: string) =>
      mono(key, label, code, 12, { placeholder: example, hint, error: JOB_PATH_ERROR });
    return [
      choice('type', 'Pipeline type', this.types, '', 12, { hint: type?.description ?? '' }),
      mono('agentLabels', 'Jenkins agents', '', 12, {
        placeholder: 'linux-agent, linux && docker',
        hint:
          'The machines the pipeline runs on: Jenkins agent labels or label expressions, separated by ' +
          'commas. The pipeline runs on an agent that matches one of them. `agentNames`',
      }),
      {
        ...job(
          'jenkinsJob',
          'Jenkins job',
          '',
          'DevSecOps/CERT/backend-api-full',
          'Where the pipeline runs in Jenkins: the path of its job, which the portal links under the ' +
            'Jenkins URL of the library defaults, or the full address of the job',
        ),
        error: 'A job path such as DevSecOps/CERT/backend-api-full, or an http or https URL',
      },
      ...(type?.value === 'SECURITY'
        ? [
            job(
              'extendedPipelineJob',
              'Extended pipeline job',
              '',
              'CERT/backend-api-extended',
              'Optional. The extended pipeline this pipeline starts after its scans. ' +
                '`jenkins.pipeline.extendedPipeline`',
            ),
          ]
        : []),
      ...(type?.value === 'EXTENDED'
        ? [
            job(
              'securityPipelineJob',
              'Security pipeline job',
              '',
              'CERT/backend-api-security',
              'The security pipeline whose build this pipeline deploys and tests. `securityPipeline`',
            ),
          ]
        : []),
      area('description', 'Description', '', 12, {
        placeholder: 'Nightly security scan of the develop branch',
        hint: "Optional. Shown on the pipeline's page.",
      }),
    ];
  });

  private template: ServiceTemplate | null = null;

  constructor() {
    const sync = (type: PipelineType) => {
      setEnabled(this.form.controls.extendedPipelineJob, type === 'SECURITY');
      setEnabled(this.form.controls.securityPipelineJob, type === 'EXTENDED');
      this.prefill();
    };
    this.form.controls.type.valueChanges.subscribe(sync);
    sync(this.form.controls.type.value);
    if (!this.editing) {
      inject(ServiceTemplateApi)
        .get()
        .pipe(
          catchError(() => of(null)),
          takeUntilDestroyed(),
        )
        .subscribe((template) => {
          this.template = template;
          this.prefill();
        });
    }
  }

  private prefill(): void {
    const { agentLabels, jenkinsJob, type } = this.form.controls;
    const template = this.template;
    if (!template) {
      return;
    }
    if (!agentLabels.dirty && template.agentLabels.length) {
      agentLabels.setValue(joinWords(template.agentLabels, ', '));
    }
    if (!jenkinsJob.dirty && this.data.productCode) {
      jenkinsJob.setValue(
        fillTemplate(
          template.jenkinsJob,
          this.data.productCode,
          this.data.service.serviceName,
          type.value,
        ),
      );
    }
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) {
      return;
    }
    const value = this.form.getRawValue();
    const request: PipelineRequest = {
      type: value.type,
      agentLabels: commaItems(value.agentLabels),
      extendedPipelineJob:
        value.type === 'SECURITY' ? value.extendedPipelineJob.trim() || null : null,
      securityPipelineJob:
        value.type === 'EXTENDED' ? value.securityPipelineJob.trim() || null : null,
      jenkinsJob: value.jenkinsJob.trim() || null,
      description: value.description.trim() || null,
      version: this.version,
    };
    const pipeline = this.data.pipeline;
    this.saving.set(true);
    this.error.set(null);
    (pipeline
      ? this.api.update(pipeline.id, request)
      : this.api.create(this.data.service.serviceId, request)
    )
      .pipe(
        catchError((error) =>
          pipeline && error instanceof HttpErrorResponse && error.status === 409
            ? this.reload(pipeline.id)
            : throwError(() => error),
        ),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (saved) => this.dialogRef.close(saved),
        error: (error) => {
          const problems = fieldProblems(error);
          const unmatched = applyFieldProblems(this.form, problems);
          this.error.set(
            problems.length
              ? unmatched.map((problem) => problem.message).join('; ') || null
              : errorMessage(error),
          );
        },
      });
  }

  private reload(id: number): Observable<never> {
    return this.api.get(id).pipe(
      tap((current) => {
        this.version = current.version;
        this.form.reset(settingsOf(current));
        this.error.set(STALE);
      }),
      catchError((error) => {
        this.error.set(
          'Not saved: someone else changed the settings of this pipeline after you opened them, ' +
            `and the current settings could not be loaded. ${errorMessage(error)}`,
        );
        return EMPTY;
      }),
      ignoreElements(),
    );
  }
}
