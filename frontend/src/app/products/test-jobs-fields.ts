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
  defaulted,
  formRevision,
  line,
  mono,
} from '@common/shared/fields';
import { DsoHelp } from '@common/ui/help';
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
    help: 'The name the remote Jenkins is configured under in the remote trigger settings of this Jenkins.',
  },
  {
    key: 'remoteJenkinsUrl',
    label: 'Remote Jenkins URL',
    span: 6,
    placeholder: 'https://perf-jenkins.bbh.com',
    code: 'remoteJenkinsUrl',
    error: HTTP_URL_ERROR,
    help: 'The address of the remote Jenkins. Together with a remote Jenkins name, it replaces only the configured address.',
  },
  {
    key: 'credentialsId',
    label: 'Credentials ID',
    span: 3,
    mono: true,
    code: 'credentialsId',
    help: 'The ID of a Jenkins Username with password credential for the remote Jenkins; the password may be an API token.',
  },
  count('pollIntervalSec', 'Poll interval (seconds)', 'pollIntervalSec', 3, {
    min: 1,
    hint: 'left empty: the stage interval',
    help: 'How often the stage asks the remote Jenkins for the result of the build.',
  }),
  mono('tokenCredentialsId', 'Token credentials ID', 'tokenCredentialsId', 3, {
    hint: 'Secret text',
    help: 'The ID of the Jenkins credential that holds the security token of the remote job. Leave it empty when the job needs no token.',
  }),
  check('abortTriggeredJob', 'Abort the remote job with this run', 'abortTriggeredJob', 4, {
    help: 'When this run is aborted, the triggered build on the remote Jenkins is stopped or removed from its queue.',
  }),
  check('preventRemoteBuildQueue', 'Wait for an idle remote job', 'preventRemoteBuildQueue', 4, {
    help: 'Holds the trigger until no other build of the job is running on the remote Jenkins.',
  }),
  check('useCrumbCache', 'Cache the crumb', 'useCrumbCache', 4, {
    help: 'Reuses the CSRF crumb of the remote Jenkins for up to 10 minutes, which helps when many jobs start at once.',
  }),
  check('trustAllCertificates', 'Trust every certificate', 'trustAllCertificates', 4, {
    help: "Accepts a self-signed or otherwise untrusted certificate of the remote Jenkins. It applies only while the remote Jenkins' certificate trust is overridden; use it only for a host you trust.",
  }),
  check(
    'overrideTrustAllCertificates',
    "Override the remote Jenkins' certificate trust",
    'overrideTrustAllCertificates',
    4,
    {
      help: 'Applies the certificate setting of this job instead of the one configured for the remote Jenkins.',
    },
  ),
  check('useJobInfoCache', 'Cache the job information', 'useJobInfoCache', 4, {
    help: 'Reuses the information about the remote job for up to 10 minutes, which helps when many jobs start at once.',
  }),
];

@Component({
  selector: 'dso-test-jobs-fields',
  imports: [Fields, DsoHelp],
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
        ...defaulted(
          value,
          this.form().controls.tests.controls.maxParallel.value,
          'left empty: global default',
        ),
        help: 'The most jobs of one test stage that run at the same time, unless the stage sets its own limit.',
      }),
    ];
  }

  protected stageFields(stage: Stage): Field[] {
    const path = `tests.${stage.noun}`;
    return [
      count(`${stage.noun}MaxParallel`, 'Parallel jobs', `${path}.maxParallel`, 0, {
        min: 1,
        help: 'The most jobs of this stage that run at the same time. Left empty, the setting for every stage applies.',
      }),
      count(
        `${stage.noun}PollIntervalSec`,
        'Poll interval (seconds)',
        `${path}.pollIntervalSec`,
        0,
        {
          min: 1,
          help: 'How often the stage asks a remote Jenkins for the result of a build, unless the job sets its own. Left empty: 15 seconds.',
        },
      ),
      check(stage.required, 'Required', `${path}.required`, 0, {
        help: 'A required stage without jobs turns orange. Clear it when these tests run elsewhere.',
      }),
    ];
  }

  protected required(stage: Stage): boolean {
    this.changes();
    return this.form().controls.tests.controls[stage.required].value;
  }

  protected jobFields(job: TestJobForm, stage: Stage): Field[] {
    return [
      line('name', 'Name', 'name', 3, {
        placeholder: 'smoke',
        help: 'The name the build log and the report show for the job. Left empty, it is derived from the job path.',
      }),
      mono('job', 'Jenkins job', isJobUrl(job.controls.job.value) ? 'url' : 'job', 6, {
        placeholder: 'CERT/gui-smoke-tests',
        hint: 'a job path, or the full URL of a job on another Jenkins',
        help: 'The stage starts this job and waits for its result. Any result other than success turns the stage orange.',
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
        {
          help: 'With Library default, a job given as a full URL runs on another Jenkins and a job path on this one.',
        },
      ),
      count('timeoutMinutes', 'Timeout (minutes)', 'timeoutMin', 3, {
        min: 1,
        help: 'How long the stage waits for the job before it reports a timeout. Left empty: 30 minutes.',
      }),
      area('parameters', 'Parameters', 'parameters', 6, {
        mono: true,
        placeholder: 'ENV=rd\nSUITE=critical',
        hint: 'One NAME=value per line',
        help: 'The parameters the job is started with, passed as text values.',
      }),
      choice(
        'stage',
        'Stage',
        STAGES.map((option) => ({ value: option.value, label: option.label })),
        `tests.${stage.noun}.jobs`,
        3,
        { help: 'The test stage that runs the job. Another stage moves the job to its list.' },
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
