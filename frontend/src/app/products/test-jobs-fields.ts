import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { GlobalSettings, TEST_STAGES, TestStage } from '../core/models';
import { HTTP_URL_ERROR, addItem, moveItem, removeItem } from '@common/shared/form-controls';
import { capitalized } from '@common/shared/formatting';
import {
  Field,
  Fields,
  area,
  check,
  choice,
  count,
  formRevision,
  line,
  mono,
} from '@common/shared/fields';
import {
  ServiceForm,
  TestJobForm,
  createTestJobForm,
  isJobUrl,
  isRemoteJob,
} from './product-form-model';

type StageNoun = 'smoke' | 'regression' | 'performance';

interface Stage {
  value: TestStage;
  label: string;
  noun: StageNoun;
  required: `${StageNoun}Required`;
}

const STAGES: Stage[] = TEST_STAGES.map((value) => {
  const noun = value.toLowerCase() as StageNoun;
  return {
    value,
    label: `${capitalized(noun)} tests`,
    noun,
    required: `${noun}Required`,
  };
});

const REMOTE: Field[] = [
  {
    key: 'remoteJenkins',
    label: 'Remote Jenkins',
    span: 3,
    placeholder: 'perf-jenkins',
    code: 'remoteJenkins',
  },
  {
    key: 'remoteJenkinsUrl',
    label: 'Remote Jenkins URL',
    span: 6,
    placeholder: 'https://perf-jenkins.bbh.com',
    code: 'remoteJenkinsUrl',
    error: HTTP_URL_ERROR,
  },
  { key: 'credentialsId', label: 'Credentials ID', span: 3, mono: true, code: 'credentialsId' },
  count('pollIntervalSec', 'Poll interval (seconds)', 'pollIntervalSec', 3, {
    min: 1,
    hint: 'left empty: the stage interval',
  }),
  mono('tokenCredentialsId', 'Token credentials ID', 'tokenCredentialsId', 3, {
    hint: 'Secret text',
  }),
  check('abortTriggeredJob', 'Abort the remote job with this run', 'abortTriggeredJob', 4),
  check('preventRemoteBuildQueue', 'Wait for an idle remote job', 'preventRemoteBuildQueue', 4),
  check('useCrumbCache', 'Cache the crumb', 'useCrumbCache', 4),
  check('trustAllCertificates', 'Trust every certificate', 'trustAllCertificates', 4),
  check(
    'overrideTrustAllCertificates',
    "Override the remote Jenkins' certificate trust",
    'overrideTrustAllCertificates',
    4,
  ),
  check('useJobInfoCache', 'Cache the job information', 'useJobInfoCache', 4),
];

@Component({
  selector: 'dso-test-jobs-fields',
  imports: [Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './test-jobs-fields.html',
  styles: `
    :host {
      display: block;
    }
    .stage-options {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px 12px;

      dso-form-field {
        width: 170px;
      }
    }
  `,
})
export class TestJobsFields {
  readonly form = input.required<ServiceForm>();
  readonly defaults = input<GlobalSettings | null>(null);

  private readonly changes = formRevision(this.form);

  protected readonly stages = STAGES;

  protected parallelFields(): Field[] {
    const value = this.defaults()?.serviceDefaults.testsMaxParallel;
    return [
      count('maxParallel', 'Parallel jobs, every stage', 'tests.maxParallel', 4, {
        min: 1,
        hint: `left empty: global default${value === undefined ? '' : ` ${value}`}`,
      }),
    ];
  }

  protected stageFields(stage: Stage): Field[] {
    const path = `tests.${stage.noun}`;
    return [
      count(`${stage.noun}MaxParallel`, 'Parallel jobs', `${path}.maxParallel`, 0, { min: 1 }),
      count(
        `${stage.noun}PollIntervalSec`,
        'Poll interval (seconds)',
        `${path}.pollIntervalSec`,
        0,
        {
          min: 1,
        },
      ),
      check(stage.required, 'Required', `${path}.required`, 0),
    ];
  }

  protected required(stage: Stage): boolean {
    this.changes();
    return this.form().controls.tests.controls[stage.required].value;
  }

  protected jobFields(job: TestJobForm, stage: Stage): Field[] {
    return [
      line('name', 'Name', 'name', 3, { placeholder: 'smoke' }),
      mono('job', 'Jenkins job', isJobUrl(job.controls.job.value) ? 'url' : 'job', 6, {
        placeholder: 'CERT/gui-smoke-tests',
        hint: 'a job path, or the full URL of a job on another Jenkins',
      }),
      choice(
        'type',
        'Runs on',
        [
          { value: null, label: 'Library default' },
          { value: 'LOCAL', label: 'This Jenkins' },
          { value: 'REMOTE', label: 'Another Jenkins' },
        ],
        'type',
        3,
      ),
      count('timeoutMinutes', 'Timeout (minutes)', 'timeoutMin', 3, { min: 1 }),
      area('parameters', 'Parameters', 'parameters', 6, {
        mono: true,
        placeholder: 'ENV=rd\nSUITE=critical',
        hint: 'One NAME=value per line',
      }),
      choice(
        'stage',
        'Stage',
        STAGES.map((option) => ({ value: option.value, label: option.label })),
        `tests.${stage.noun}.jobs`,
        3,
      ),
      ...(isRemoteJob(job) ? REMOTE : []),
    ];
  }

  protected jobsOf(stage: TestStage): TestJobForm[] {
    this.changes();
    return this.form().controls.testJobs.controls.filter(
      (job) => job.controls.stage.value === stage,
    );
  }

  protected add(stage: TestStage): void {
    addItem(this.form().controls.testJobs, createTestJobForm({ stage }));
  }

  protected remove(job: TestJobForm): void {
    const jobs = this.form().controls.testJobs;
    removeItem(jobs, jobs.controls.indexOf(job));
  }

  protected move(job: TestJobForm, offset: -1 | 1): void {
    const jobs = this.form().controls.testJobs;
    const sameStage = this.jobsOf(job.controls.stage.value);
    const neighbour = sameStage[sameStage.indexOf(job) + offset];
    if (neighbour) {
      moveItem(jobs, jobs.controls.indexOf(job), jobs.controls.indexOf(neighbour));
    }
  }

  protected jobName(job: TestJobForm): string {
    return job.controls.name.value || job.controls.job.value || 'New job';
  }
}
