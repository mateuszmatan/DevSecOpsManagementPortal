import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GlobalSettings, TestJob } from '../core/models';
import { applyFieldProblems } from '@common/shared/form-controls';
import { buttonOf, checkboxOf, fieldOf, inputOf, text } from '@common/testing/dom';
import { globalSettings, service } from '../testing/fixtures';
import { ServiceForm, createServiceForm } from './product-form-model';
import { TestJobsFields } from './test-jobs-fields';

describe('TestJobsFields', () => {
  let fixture: ComponentFixture<TestJobsFields>;
  let form: ServiceForm;

  const job = (overrides: Partial<TestJob>): TestJob => ({
    stage: 'SMOKE',
    name: null,
    type: 'LOCAL',
    job: 'CERT/gui-smoke',
    timeoutMinutes: null,
    parameters: null,
    remoteJenkins: null,
    remoteJenkinsUrl: null,
    credentialsId: null,
    pollIntervalSec: null,
    tokenCredentialsId: null,
    abortTriggeredJob: false,
    overrideTrustAllCertificates: false,
    preventRemoteBuildQueue: false,
    trustAllCertificates: false,
    useCrumbCache: false,
    useJobInfoCache: false,
    ...overrides,
  });

  async function render(jobs: TestJob[], defaults: GlobalSettings | null = globalSettings()) {
    TestBed.configureTestingModule({ imports: [TestJobsFields] });
    form = createServiceForm(service({ testJobs: jobs }));
    fixture = TestBed.createComponent(TestJobsFields);
    fixture.componentRef.setInput('form', form);
    fixture.componentRef.setInput('defaults', defaults);
    await fixture.whenStable();
  }

  const page = () => fixture.nativeElement as HTMLElement;
  const sections = () => [...page().querySelectorAll<HTMLElement>('.list-section')];
  const names = () =>
    form.controls.testJobs.controls.map(
      (control) => control.controls.name.value || control.controls.job.value,
    );

  it('takes the parameters as one NAME=value per line in a text area', async () => {
    await render([job({ stage: 'REGRESSION', parameters: 'ENV=rd\nSUITE=critical' })]);

    const textarea = inputOf(page(), 'Parameters') as unknown as HTMLTextAreaElement;
    expect(textarea.value).toBe('ENV=rd\nSUITE=critical');
    expect(text(textarea.closest('dso-form-field'))).toContain(
      'One NAME=value per line · parameters',
    );

    textarea.value = 'ENV=rd\nSUITE critical';
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new Event('blur'));
    await fixture.whenStable();

    expect(text(textarea.closest('dso-form-field'))).toContain(
      'Write each parameter as NAME=value: SUITE critical',
    );
  });

  it('lists the jobs of each stage with their counts and parallel limits', async () => {
    await render([
      job({ name: 'smoke' }),
      job({ stage: 'REGRESSION', name: 'regression' }),
      job({ stage: 'REGRESSION', name: 'nightly' }),
    ]);

    expect(sections().map((section) => section.querySelector('h4')?.textContent?.trim())).toEqual([
      'Smoke tests 1',
      'Regression tests 2',
      'Performance tests 0',
    ]);
    expect(sections()[2].querySelector('.list-empty')?.textContent).toBe(
      'No performance test jobs.',
    );
    expect(text(page())).toContain('Left empty: global default 20 · tests.maxParallel');
  });

  it('adds, moves and removes jobs within a stage', async () => {
    await render([
      job({ stage: 'REGRESSION', name: 'first' }),
      job({ stage: 'SMOKE', name: 'smoke' }),
      job({ stage: 'REGRESSION', name: 'second' }),
    ]);

    buttonOf(sections()[1], 'Down').click();
    await fixture.whenStable();
    expect(names()).toEqual(['smoke', 'second', 'first']);
    expect(form.dirty).toBe(true);

    buttonOf(sections()[1], 'Add regression job').click();
    await fixture.whenStable();
    expect(form.controls.testJobs.length).toBe(4);
    expect(sections()[1].querySelectorAll('.list-item').length).toBe(3);

    buttonOf(sections()[1], 'Remove').click();
    await fixture.whenStable();
    expect(names()).toEqual(['smoke', 'first', '']);
  });

  it('shows a problem the API reported for the whole list of jobs', async () => {
    await render([job({ name: 'smoke' })]);

    applyFieldProblems(form, [{ field: 'testJobs', message: 'Too long: at most 4000 characters' }]);
    fixture.changeDetectorRef.markForCheck();
    await fixture.whenStable();

    expect(page().querySelector('.list-error[role=alert]')?.textContent?.trim()).toBe(
      'Too long: at most 4000 characters',
    );
  });

  it('shows the remote Jenkins fields only for a remote job', async () => {
    await render([
      job({ name: 'local' }),
      job({ name: 'remote', type: 'REMOTE', remoteJenkins: 'qa' }),
    ]);

    const items = [...page().querySelectorAll('.list-item')];
    expect(fieldOf(items[0], 'Remote Jenkins')).toBeNull();
    expect(fieldOf(items[0], 'Token credentials ID')).toBeNull();
    expect(inputOf(items[1], 'Remote Jenkins').value).toBe('qa');
    expect(text(fieldOf(items[1], 'Token credentials ID'))).toContain('Secret text');
    expect(checkboxOf(items[1], 'Cache the crumb')).toBeDefined();
    expect(text(fieldOf(items[1], 'Poll interval (seconds)'))).toContain('the stage interval');
  });

  it('marks a stage not required and takes its poll interval', async () => {
    await render([job({ stage: 'REGRESSION', name: 'nightly' })]);
    const smoke = () => sections()[0];
    expect(fieldOf(smoke(), 'Poll interval (seconds)')).not.toBeNull();
    expect(smoke().querySelector('.list-empty')?.textContent).toBe('No smoke test jobs.');

    checkboxOf(smoke(), 'Required').click();
    await fixture.whenStable();

    expect(form.controls.tests.controls.smokeRequired.value).toBe(false);
    expect(text(smoke().querySelector('.list-empty'))).toContain('Not required');
    expect(text(sections()[1])).not.toContain('Not required');
  });
});
