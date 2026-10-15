import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, map } from 'rxjs';
import { ServiceTemplateApi } from '../core/api';
import { errorMessage, fieldProblems } from '@common/core/errors';
import { ServiceTemplate, ServiceTemplateRequest, ServiceTemplateValues } from '../core/models';
import { Notifier } from '@common/core/notifier';
import { HasUnsavedChanges } from '@common/core/unsaved-changes';
import { AGENT_LABEL } from '../products/pipeline-dialog';
import { Field, Fields, mono } from '@common/shared/fields';
import {
  IMAGE_TAG,
  IMAGE_TAG_ERROR,
  SHELL_SAFE_ERROR,
  applyFieldProblems,
  commaItems,
  eachItem,
  filled,
  fitsColumn,
  joinWords,
  max,
  optional,
  shellSafe,
  text,
  url,
} from '@common/shared/form-controls';
import { RelativeTimePipe } from '@common/shared/formatting';
import {
  JOB_PLACEHOLDERS,
  SERVICE_PLACEHOLDERS,
  fillTemplate,
  knownPlaceholders,
} from '../shared/service-template';
import { FieldProblem } from '@common/core/models';
import { DsoLoading, DsoSpinner } from '@common/ui/loading';

const MAX_LABELS = 20;
const JOB_PATTERN = /^(?!.*\.\.)[A-Za-z0-9._ /{}-]*$/;
const NEXUS_IQ_PATTERN = /^[A-Za-z0-9._{}-]*$/;
const PROJECT_PATTERN = /^[A-Za-z0-9{}-]*$/;
const EXAMPLE = { code: 'CERT', service: 'backend-api' };

type TemplateKey = keyof ServiceTemplateValues;

interface TemplateSection {
  heading: string;
  note: string;
  fields: Field[];
}

export function createTemplateForm() {
  const service = knownPlaceholders(SERVICE_PLACEHOLDERS);
  return new FormGroup({
    agentLabels: text(
      '',
      filled,
      (control) =>
        commaItems(control.value).length > MAX_LABELS ? { maxItems: { max: MAX_LABELS } } : null,
      eachItem(commaItems, AGENT_LABEL, 'At most 100 characters per label'),
      fitsColumn(commaItems, ',', 1000),
    ),
    jenkinsJob: text(
      '',
      Validators.pattern(JOB_PATTERN),
      max(500),
      knownPlaceholders(JOB_PLACEHOLDERS),
    ),
    gradleTasks: text('', max(500)),
    gradleArtifact: shellSafe('', 500),
    gradleScanPattern: text('', max(300)),
    mavenTasks: text('', max(500)),
    mavenArtifact: shellSafe('', 500),
    mavenScanPattern: text('', max(300)),
    flutterScanPattern: text('', max(300)),
    deliveryTasks: text('', max(500)),
    nexusIqApplication: text('', Validators.pattern(NEXUS_IQ_PATTERN), max(200), service),
    repositoryUrl: url('', 1000, service),
    bitbucketCredentialsId: text('', max(200)),
    openShiftProject: text('', Validators.pattern(PROJECT_PATTERN), max(100), service),
    imageRegistry: text('', Validators.pattern(IMAGE_TAG), max(300)),
    healthCheckUrl: text('', max(500)),
  });
}

export type TemplateForm = ReturnType<typeof createTemplateForm>;

export function patchTemplate(form: TemplateForm, template: ServiceTemplateValues): void {
  const { agentLabels, ...values } = template;
  form.reset({
    ...Object.fromEntries(Object.entries(values).map(([key, value]) => [key, value ?? ''])),
    agentLabels: joinWords(agentLabels, ', '),
  });
}

export function toTemplateRequest(
  form: TemplateForm,
  version: number | null,
): ServiceTemplateRequest {
  const { agentLabels, ...values } = form.getRawValue();
  return {
    ...(Object.fromEntries(
      Object.entries(values).map(([key, value]) => [key, optional(value)]),
    ) as Omit<ServiceTemplateValues, 'agentLabels'>),
    agentLabels: commaItems(agentLabels),
    version,
  };
}

const SECTIONS: TemplateSection[] = [
  {
    heading: 'Pipelines',
    note: 'Where a new pipeline runs and the Jenkins job it gets. The job name depends on the pipeline type.',
    fields: [
      mono('agentLabels', 'Jenkins agents', '', 6, {
        placeholder: 'linux-agent',
        hint: 'The machines the pipeline runs on, as Jenkins agent labels separated by commas',
      }),
      mono('jenkinsJob', 'Jenkins job', '', 6, {
        placeholder: 'DevSecOps/{CODE}/{service}-{type}',
        hint: 'Where the pipeline lives in Jenkins',
        error: "A job path with letters, digits, spaces, . _ / - and placeholders, without '..'",
      }),
    ],
  },
  {
    heading: 'Build',
    note: 'How a new service is built, by its build tool, and what the security pipeline publishes.',
    fields: [
      mono('gradleTasks', 'Gradle tasks', '', 6, {
        placeholder: 'clean build',
        hint: 'What a Gradle build runs',
      }),
      mono('gradleArtifact', 'Gradle artifact', '', 6, {
        placeholder: 'build/libs/*.jar',
        hint: 'The file a Gradle build produces',
        error: SHELL_SAFE_ERROR,
      }),
      mono('mavenTasks', 'Maven goals', '', 6, {
        placeholder: 'clean verify',
        hint: 'What a Maven build runs',
      }),
      mono('mavenArtifact', 'Maven artifact', '', 6, {
        placeholder: 'target/*.jar',
        hint: 'The file a Maven build produces',
        error: SHELL_SAFE_ERROR,
      }),
      mono('deliveryTasks', 'Nexus delivery goals', '', 6, {
        placeholder: 'deploy:deploy-file',
        hint: 'How a Maven build is delivered to Nexus',
      }),
    ],
  },
  {
    heading: 'Nexus IQ and Bitbucket',
    note: 'Where the Nexus IQ GoldenFix pipeline checks the open source libraries of a service and opens its upgrade pull requests.',
    fields: [
      mono('nexusIqApplication', 'Nexus IQ application', '', 6, {
        placeholder: '{code}-{service}',
        hint: 'The application in Nexus IQ the libraries are checked against',
        error: 'Letters, digits, . _ - and placeholders only',
      }),
      mono('repositoryUrl', 'Bitbucket repository', '', 6, {
        placeholder: 'https://bitbucket.bbh.com/projects/{CODE}/repos/{code}-{service}',
        hint: 'Where GoldenFix opens its pull requests',
      }),
      mono('bitbucketCredentialsId', 'Bitbucket credentials ID', '', 6, {
        placeholder: 'bitbucket-http-credentials',
        hint: 'The Jenkins credentials that open the pull requests',
      }),
      mono('gradleScanPattern', 'Gradle scan pattern', '', 6, {
        placeholder: '**/build/libs/*.jar',
        hint: 'The files Nexus IQ checks in a Gradle build',
      }),
      mono('mavenScanPattern', 'Maven scan pattern', '', 6, {
        placeholder: '**/target/*.jar',
        hint: 'The files Nexus IQ checks in a Maven build',
      }),
      mono('flutterScanPattern', 'Flutter scan pattern', '', 6, {
        placeholder: '**/pubspec.lock',
        hint: 'The files Nexus IQ checks in a Flutter build',
      }),
    ],
  },
  {
    heading: 'OpenShift',
    note: 'Where a new service that runs on OpenShift is built and runs.',
    fields: [
      mono('openShiftProject', 'OpenShift project', '', 6, {
        placeholder: '{code}-{service}',
        hint: 'Written in lower case; the projects end in -build, -rd and -qc',
        error: 'Letters, digits, - and placeholders only',
      }),
      mono('imageRegistry', 'Image registry', '', 6, {
        placeholder: 'docker-qc.tools.bbh.com',
        hint: 'Where the images of the service are stored',
        error: IMAGE_TAG_ERROR,
      }),
      mono('healthCheckUrl', 'Health check path', '', 6, {
        placeholder: '/actuator/health',
        hint: 'The address OpenShift calls to check that the service is up',
      }),
    ],
  },
];

const PLACEHOLDER_MEANINGS: Record<(typeof JOB_PLACEHOLDERS)[number], string> = {
  CODE: `the product code, as in ${EXAMPLE.code}`,
  code: `the product code in lower case, as in ${EXAMPLE.code.toLowerCase()}`,
  service: `the service name, as in ${EXAMPLE.service}`,
  type: 'the pipeline type, in the Jenkins job only: full, security, extended, sast or nexusiq',
};

const EXAMPLES: { key: TemplateKey; label: string }[] = [
  { key: 'jenkinsJob', label: 'Full pipeline job' },
  { key: 'nexusIqApplication', label: 'Nexus IQ application' },
  { key: 'repositoryUrl', label: 'Bitbucket repository' },
  { key: 'openShiftProject', label: 'OpenShift projects' },
];

@Component({
  selector: 'dso-service-template-page',
  imports: [ReactiveFormsModule, DsoLoading, DsoSpinner, Fields, RelativeTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="tab-header">
      <p class="meta">
        What a new service and its pipelines get. Self-service and the Add service and Add pipeline
        forms fill in these values, and each one can still be changed there. Fields marked * are
        required.
      </p>
    </header>

    @if (loading()) {
      <dso-loading />
    }

    @if (loadError(); as error) {
      <div class="banner" role="alert">
        <span class="banner-text">The service template could not be loaded: {{ error }}</span>
        <button type="button" class="btn btn-outline-primary" (click)="load()">Try again</button>
      </div>
    } @else if (template(); as t) {
      @if (conflict()) {
        <div class="banner danger conflict" role="alert">
          <div class="banner-text">
            <strong>Someone else saved the template after you opened this page.</strong>
            Your changes were not saved. Reload to get the current values, then make your changes
            again.
          </div>
          <button type="button" class="btn btn-primary" (click)="load()">Reload</button>
        </div>
      }
      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        @if (unmatchedProblems().length) {
          <div class="banner" role="alert">
            <ul class="problems">
              @for (problem of unmatchedProblems(); track problem.field + problem.message) {
                <li>
                  <span class="mono">{{ problem.field }}</span
                  >: {{ problem.message }}
                </li>
              }
            </ul>
          </div>
        }
        <div class="layout">
          <div>
            @for (section of sections; track section.heading) {
              <section class="card section">
                <header class="section-head">
                  <h2>{{ section.heading }}</h2>
                  <p class="section-help">{{ section.note }}</p>
                </header>
                <div class="form-fields">
                  <dso-fields [group]="form" [fields]="section.fields" />
                </div>
              </section>
            }
          </div>
          <aside class="card section example" aria-label="What a new service gets">
            <header class="section-head">
              <h2>What a new service gets</h2>
              <p class="section-help">
                The service <strong>{{ example.service }}</strong> of the product
                <strong>{{ example.code }}</strong> would get these values. They change as you type.
              </p>
            </header>
            <dl class="pairs">
              @for (line of examples(); track line.label) {
                <div>
                  <dt>{{ line.label }}</dt>
                  <dd class="mono">{{ line.value }}</dd>
                </div>
              }
            </dl>
            <h3>Placeholders</h3>
            <p class="section-help">A placeholder in a value is replaced for each new service:</p>
            <dl class="rows placeholders">
              @for (placeholder of placeholders; track placeholder.name) {
                <dt>
                  <code>{{ '{' + placeholder.name + '}' }}</code>
                </dt>
                <dd>{{ placeholder.meaning }}</dd>
              }
            </dl>
          </aside>
        </div>
        <div class="save-bar">
          @if (saveError(); as error) {
            <span class="save-error" role="alert">{{ error }}</span>
          } @else if (form.dirty) {
            <span class="muted">Unsaved changes</span>
          } @else if (t.version === null) {
            <span class="muted saved">Not saved yet: these are the BBH defaults</span>
          } @else {
            <span class="muted saved"
              >Last saved {{ t.updatedAt | relative }} (version {{ t.version }})</span
            >
          }
          <span class="spacer"></span>
          <button
            type="button"
            class="btn btn-link"
            (click)="discard()"
            [disabled]="!form.dirty || saving()"
          >
            Discard changes
          </button>
          <button type="submit" class="btn btn-primary" [disabled]="saving() || conflict()">
            @if (saving()) {
              <dso-spinner />
            }
            Save template
          </button>
        </div>
      </form>
    }
  `,
  styles: `
    .tab-header {
      margin-bottom: 12px;
    }

    .meta {
      margin: 0;
      max-width: 900px;
      color: var(--dso-muted);
      font-size: 12px;
    }

    .layout {
      display: grid;
      grid-template-columns: minmax(0, 1fr) 360px;
      gap: 12px;
      align-items: start;
    }

    .section {
      margin-bottom: 12px;
      padding: 12px 16px 4px;
    }

    .section-head {
      padding-bottom: 8px;
      border-bottom: 1px solid var(--dso-border);

      h2 {
        margin: 0 0 2px;
        font-size: 17px;
      }

      .section-help {
        margin: 0;
      }

      + .form-fields {
        padding: 10px 0 8px;
      }
    }

    .example {
      position: sticky;
      top: 64px;
      padding-bottom: 12px;
      border-left: 3px solid var(--dso-navy);

      .pairs {
        flex-direction: column;
        gap: 8px;
        margin: 10px 0 14px;

        div {
          flex-direction: column;
          gap: 0;
        }

        dd {
          font-size: 12.5px;
          color: var(--dso-navy);
        }
      }

      h3 {
        margin: 0 0 2px;
        padding-top: 10px;
        border-top: 1px solid var(--dso-border);
        font-size: 14px;
        font-weight: 600;
      }

      .section-help {
        margin: 0 0 6px;
      }

      .placeholders {
        font-size: 12px;
      }
    }

    .banner-text {
      flex: 1;
    }

    @media (max-width: 1100px) {
      .layout {
        grid-template-columns: minmax(0, 1fr);
        gap: 0;
      }

      .example {
        position: static;
        order: -1;
      }
    }
  `,
})
export class ServiceTemplatePage implements OnInit, HasUnsavedChanges {
  private readonly api = inject(ServiceTemplateApi);
  private readonly notifier = inject(Notifier);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = createTemplateForm();
  protected readonly sections = SECTIONS;
  protected readonly example = EXAMPLE;
  protected readonly placeholders = JOB_PLACEHOLDERS.map((name) => ({
    name,
    meaning: PLACEHOLDER_MEANINGS[name],
  }));
  protected readonly template = signal<ServiceTemplate | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly conflict = signal(false);
  protected readonly unmatchedProblems = signal<FieldProblem[]>([]);

  private readonly values = toSignal(
    this.form.valueChanges.pipe(map(() => this.form.getRawValue())),
    { initialValue: this.form.getRawValue() },
  );
  protected readonly examples = computed(() => {
    const values = this.values();
    const filledIn = (key: TemplateKey) =>
      fillTemplate(
        values[key as Exclude<TemplateKey, 'agentLabels'>],
        EXAMPLE.code,
        EXAMPLE.service,
        'FULL',
      ) || 'none';
    return EXAMPLES.map(({ key, label }) => {
      const value = filledIn(key);
      return {
        label,
        value:
          key === 'openShiftProject' && value !== 'none'
            ? ['build', 'rd', 'qc'].map((suffix) => `${value.toLowerCase()}-${suffix}`).join(', ')
            : value,
      };
    });
  });

  ngOnInit(): void {
    this.load();
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty;
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
        next: (template) => this.apply(template),
        error: (error) => this.loadError.set(errorMessage(error)),
      });
  }

  protected discard(): void {
    const template = this.template();
    if (template) {
      this.apply(template);
    }
  }

  protected save(): void {
    this.saveError.set(null);
    this.conflict.set(false);
    this.unmatchedProblems.set([]);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.saveError.set('Some fields need your attention.');
      return;
    }
    this.saving.set(true);
    this.api
      .update(toTemplateRequest(this.form, this.template()?.version ?? null))
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (template) => {
          this.apply(template);
          this.notifier.success(
            'Service template saved. New services and pipelines get these values from now on.',
          );
        },
        error: (error) => this.showSaveError(error),
      });
  }

  private apply(template: ServiceTemplate): void {
    patchTemplate(this.form, template);
    this.template.set(template);
    this.saveError.set(null);
    this.conflict.set(false);
    this.unmatchedProblems.set([]);
  }

  private showSaveError(error: unknown): void {
    if (error instanceof HttpErrorResponse && error.status === 409) {
      this.conflict.set(true);
      this.saveError.set('Not saved: the template was changed by someone else.');
      return;
    }
    const problems = fieldProblems(error);
    if (problems.length === 0) {
      this.saveError.set(errorMessage(error));
      return;
    }
    this.unmatchedProblems.set(applyFieldProblems(this.form, problems));
    this.saveError.set('The portal did not accept some values. They are marked above.');
  }
}
