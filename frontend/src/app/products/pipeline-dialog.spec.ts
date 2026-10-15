import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { inputOf, text } from '@common/testing/dom';
import { pipeline, servicePipelines, serviceTemplate } from '../testing/fixtures';
import { ServiceTemplate } from '../core/models';
import { PipelineDialog, PipelineDialogData } from './pipeline-dialog';

describe('PipelineDialog', () => {
  let fixture: ComponentFixture<PipelineDialog>;
  let http: HttpTestingController;
  const close = vi.fn();

  async function render(
    data: PipelineDialogData,
    template: ServiceTemplate | null = serviceTemplate(),
  ) {
    TestBed.configureTestingModule({
      imports: [PipelineDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DIALOG_DATA, useValue: data },
        { provide: DialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PipelineDialog);
    if (!data.pipeline) {
      const request = http.expectOne('/api/service-template');
      if (template) {
        request.flush(template);
      } else {
        request.flush(null, { status: 500, statusText: 'Server Error' });
      }
    }
    await fixture.whenStable();
  }

  afterEach(() => {
    http.verify();
    close.mockReset();
  });

  const dialog = () => fixture.componentInstance;
  const form = () => dialog()['form'];
  const page = () => fixture.nativeElement as HTMLElement;
  const submit = async () => {
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  };
  const type = async (label: string, value: string) => {
    const input = inputOf(page(), label);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
  };
  const labels = () =>
    [...page().querySelectorAll('dso-label')].map((label) => label.textContent?.trim());
  const every = () =>
    servicePipelines({
      pipelines: (['FULL', 'SECURITY', 'EXTENDED', 'SAST', 'NEXUS_IQ'] as const).map(
        (value, index) => pipeline({ id: 100 + index, type: value }),
      ),
    });

  it('offers only the types the service has no pipeline of', async () => {
    await render({ service: servicePipelines({ pipelines: [pipeline()] }) });

    expect(dialog()['types'].map((type) => type.value)).not.toContain('FULL');
  });

  it('keeps the type of a stored pipeline and shows only its own job field', async () => {
    const stored = pipeline({ type: 'SECURITY', extendedPipelineJob: 'CERT/gui-extended' });
    await render({ service: servicePipelines({ pipelines: [stored] }), pipeline: stored });

    expect(form().controls.type.disabled).toBe(true);
    expect(form().controls.extendedPipelineJob.enabled).toBe(true);
    expect(form().controls.securityPipelineJob.disabled).toBe(true);
  });

  it('starts a new pipeline with the default agent and explains its key', async () => {
    await render({ service: servicePipelines({ pipelines: [] }) });

    expect(page().querySelector('h2')?.textContent).toBe('Add a pipeline to gui');
    expect(text(page().querySelector('.intro'))).toBe(
      "The new pipeline of gui gets its own pipeline key: the secret code the service's Jenkins job uses to fetch its settings from this portal.",
    );
    expect(inputOf(page(), 'Jenkins agents').value).toBe('linux-agent');
    expect(page().querySelector('dso-hint')?.textContent).toContain(
      'Build, scans, tests, deployment and release',
    );
    expect(labels()).not.toContain('Extended pipeline job');
    expect(labels()).not.toContain('Security pipeline job');
  });

  it('says when the service has a pipeline of every type and adds nothing', async () => {
    await render({ service: every() });

    expect(page().querySelector('.banner.info')?.textContent).toBe(
      'The service already has a pipeline of every type.',
    );
    expect(page().querySelector('select')).toBeNull();
    expect(page().querySelector<HTMLButtonElement>('button[type=submit]')?.disabled).toBe(true);
  });

  it('shows the security pipeline field of an extended pipeline', async () => {
    const stored = pipeline({
      type: 'EXTENDED',
      securityPipelineJob: 'CERT/gui-security',
      description: 'Deploys to QC',
    });
    await render({ service: servicePipelines({ pipelines: [stored] }), pipeline: stored });

    expect(page().querySelector('h2')?.textContent).toBe('Settings of the pipeline gui · Extended');
    expect(text(page().querySelector('.intro'))).toBe(
      'Where and how Jenkins runs this pipeline of gui. Its type cannot be changed.',
    );
    expect(labels()).toContain('Security pipeline job');
    expect(labels()).not.toContain('Extended pipeline job');
    expect(inputOf(page(), 'Security pipeline job').value).toBe('CERT/gui-security');
    expect(page().querySelector('button[type=submit]')?.textContent?.trim()).toBe('Save settings');
  });

  it('saves the settings of a stored pipeline with trimmed values', async () => {
    const stored = pipeline();
    await render({ service: servicePipelines({ pipelines: [stored] }), pipeline: stored });

    await type('Jenkins agents', ' linux-agent,  docker ');
    await type('Jenkins job', '');
    await type('Description', '  Release build  ');
    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/pipelines/100' });
    expect(request.request.body).toEqual({
      type: 'FULL',
      agentLabels: ['linux-agent', 'docker'],
      extendedPipelineJob: null,
      securityPipelineJob: null,
      jenkinsJob: null,
      description: 'Release build',
      version: 3,
    });
    expect(page().querySelector('dso-spinner')).not.toBeNull();
    await submit();
    http.expectNone({ method: 'PUT', url: '/api/pipelines/100' });

    request.flush(pipeline({ agentLabels: ['linux-agent', 'docker'] }));
    expect(close).toHaveBeenCalledWith(pipeline({ agentLabels: ['linux-agent', 'docker'] }));
  });

  it('reloads the settings someone else saved meanwhile and saves the next change on top', async () => {
    const stored = pipeline();
    await render({ service: servicePipelines({ pipelines: [stored] }), pipeline: stored });

    await type('Description', 'Release build');
    await submit();
    http
      .expectOne({ method: 'PUT', url: '/api/pipelines/100' })
      .flush(
        { detail: 'The record was changed by someone else in the meantime.' },
        { status: 409, statusText: 'Conflict' },
      );
    const current = pipeline({ version: 4, jenkinsJob: 'DevSecOps/CERT/gui-full-v2' });
    http.expectOne({ method: 'GET', url: '/api/pipelines/100' }).flush(current);
    await fixture.whenStable();

    expect(page().querySelector('[role=alert]')?.textContent).toBe(
      'Not saved: someone else changed the settings of this pipeline after you opened them. The form now shows their settings: make your change again, then save.',
    );
    expect(inputOf(page(), 'Jenkins job').value).toBe('DevSecOps/CERT/gui-full-v2');
    expect(inputOf(page(), 'Description').value).toBe('');
    expect(page().querySelector('dso-spinner')).toBeNull();
    expect(close).not.toHaveBeenCalled();

    await type('Description', 'Release build');
    await submit();
    const request = http.expectOne({ method: 'PUT', url: '/api/pipelines/100' });
    expect(request.request.body).toMatchObject({
      jenkinsJob: 'DevSecOps/CERT/gui-full-v2',
      description: 'Release build',
      version: 4,
    });
    request.flush(current);
    expect(close).toHaveBeenCalledWith(current);
  });

  it('says when the settings someone else saved meanwhile could not be loaded', async () => {
    const stored = pipeline();
    await render({ service: servicePipelines({ pipelines: [stored] }), pipeline: stored });

    await submit();
    http
      .expectOne({ method: 'PUT', url: '/api/pipelines/100' })
      .flush(null, { status: 409, statusText: 'Conflict' });
    http
      .expectOne({ method: 'GET', url: '/api/pipelines/100' })
      .flush(null, { status: 0, statusText: 'Unknown Error' });
    await fixture.whenStable();

    expect(page().querySelector('[role=alert]')?.textContent).toBe(
      'Not saved: someone else changed the settings of this pipeline after you opened them, and the current settings could not be loaded. The portal cannot be reached. Check your network connection and try again.',
    );
    expect(close).not.toHaveBeenCalled();
  });

  it('adds a Nexus IQ GoldenFix pipeline without a job of another pipeline', async () => {
    const service = servicePipelines({
      pipelines: (['FULL', 'SECURITY', 'EXTENDED', 'SAST'] as const).map((value, index) =>
        pipeline({ id: 100 + index, type: value }),
      ),
    });
    await render({ service });

    expect(dialog()['types'].map((type) => type.label)).toEqual(['Nexus IQ GoldenFix']);
    expect(form().controls.type.value).toBe('NEXUS_IQ');
    expect(page().querySelector('dso-hint')?.textContent).toContain(
      "GoldenFix opens a pull request with safe versions in the service's Bitbucket repository",
    );
    expect(labels()).toEqual(['Pipeline type', 'Jenkins agents', 'Jenkins job', 'Description']);

    await type('Jenkins job', 'DevSecOps/CERT/gui-nexusiq');
    await submit();

    const request = http.expectOne({ method: 'POST', url: '/api/services/10/pipelines' });
    expect(request.request.body).toEqual({
      type: 'NEXUS_IQ',
      agentLabels: ['linux-agent'],
      extendedPipelineJob: null,
      securityPipelineJob: null,
      jenkinsJob: 'DevSecOps/CERT/gui-nexusiq',
      description: null,
      version: null,
    });
    const created = pipeline({ id: 104, type: 'NEXUS_IQ' });
    request.flush(created);
    expect(close).toHaveBeenCalledWith(created);
  });

  it('refuses unusable agent labels and job paths before sending', async () => {
    await render({ service: servicePipelines({ pipelines: [] }) });

    await type('Jenkins agents', `linux && docker, ${'x'.repeat(101)}`);
    await type('Jenkins job', 'DevSecOps/CERT?branch=main');
    await submit();

    http.expectNone('/api/services/10/pipelines');
    const errors = [...page().querySelectorAll('dso-error')].map((e) => e.textContent?.trim());
    expect(errors).toContain(`At most 100 characters per label: ${'x'.repeat(101)}`);
    expect(errors).toContain(
      'A job path such as DevSecOps/CERT/backend-api-full, or an http or https URL',
    );
    expect(close).not.toHaveBeenCalled();
  });

  it('marks the fields the API refused and lists the problems without a field', async () => {
    await render({ service: servicePipelines({ pipelines: [] }) });

    await submit();
    http.expectOne('/api/services/10/pipelines').flush(
      {
        title: 'Bad Request',
        detail: 'The request has invalid values',
        errors: [
          { field: 'jenkinsJob', message: 'is used by the full pipeline of api' },
          { field: 'serviceId', message: 'the service was deleted' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(form().controls.jenkinsJob.errors).toEqual({
      server: 'is used by the full pipeline of api',
    });
    expect(page().querySelector('[role=alert]')?.textContent).toBe('the service was deleted');
    expect(page().querySelector('dso-spinner')).toBeNull();
    expect(close).not.toHaveBeenCalled();
  });

  it('fills the agents and the job of a new pipeline from the service template', async () => {
    await render(
      {
        service: servicePipelines({ serviceName: 'backend-api', pipelines: [pipeline()] }),
        productCode: 'CERT',
      },
      serviceTemplate({
        agentLabels: ['linux', 'docker'],
        jenkinsJob: 'Teams/{CODE}/{service}/{type}',
      }),
    );

    expect(inputOf(page(), 'Jenkins agents').value).toBe('linux, docker');
    expect(inputOf(page(), 'Jenkins job').value).toBe('Teams/CERT/backend-api/security');

    form().controls.type.setValue('SAST');
    await fixture.whenStable();
    expect(inputOf(page(), 'Jenkins job').value).toBe('Teams/CERT/backend-api/sast');

    await type('Jenkins job', 'Teams/CERT/own-job');
    form().controls.type.setValue('EXTENDED');
    await fixture.whenStable();
    expect(inputOf(page(), 'Jenkins job').value).toBe('Teams/CERT/own-job');
  });

  it('keeps the BBH agent and an empty job when the template cannot be read', async () => {
    await render({ service: servicePipelines({ pipelines: [] }), productCode: 'CERT' }, null);

    expect(inputOf(page(), 'Jenkins agents').value).toBe('linux-agent');
    expect(inputOf(page(), 'Jenkins job').value).toBe('');
  });
});
