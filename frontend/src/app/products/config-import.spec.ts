import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { FormControl } from '@angular/forms';
import { of } from 'rxjs';
import { ImportedService, ServiceConfigImport } from '../core/models';
import { Notifier } from '@common/core/notifier';
import { buttonOf, helpOf, text } from '@common/testing/dom';
import { nodeBuild, service } from '../testing/fixtures';
import {
  ConfigImport,
  ConfigServicePicker,
  ImportNotes,
  ImportedConfig,
  MAX_CONFIG_BYTES,
  importedServiceForm,
  noteGroups,
  prefillAgents,
} from './config-import';
import { toServiceRequest } from './product-form-model';

const CONFIG = 'tools:\n  build:\n    tool: gradle\n';
const URL = '/api/service-configs/import';

// The File of jsdom cannot read its content.
const upload = (content = CONFIG, name = 'config.yaml') =>
  Object.assign(new File([content], name), { text: () => Promise.resolve(content) });

const imported = (name: string, notes = [{ field: `${name}.tools.x`, message: 'check it' }]) =>
  ({ name, service: { ...service(), name }, notes }) satisfies ImportedService;

describe('ConfigImport', () => {
  let fixture: ComponentFixture<ConfigImport>;
  let http: HttpTestingController;
  let emitted: ImportedConfig[];
  let error: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [ConfigImport],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    error = vi.spyOn(TestBed.inject(Notifier), 'error').mockReturnValue();
    fixture = TestBed.createComponent(ConfigImport);
    fixture.componentRef.setInput('serviceName', ' portal ');
    emitted = [];
    fixture.componentInstance.imported.subscribe((config) => emitted.push(config));
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const button = () => buttonOf(fixture.nativeElement, 'Import devsecops_config.yaml');
  const fileInput = () =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[type=file]')!;

  async function pick(...files: File[]) {
    const input = fileInput();
    Object.defineProperty(input, 'files', {
      value: files.length ? files : [upload()],
      configurable: true,
    });
    input.dispatchEvent(new Event('change'));
  }

  const sent = () => vi.waitFor(() => http.expectOne({ method: 'POST', url: URL }));

  async function answer(result: Partial<ServiceConfigImport>) {
    (await sent()).flush({ services: [], pipeline: null, notes: [], ...result });
    await fixture.whenStable();
  }

  it('sends the file with the typed service name and imports its one service with every note', async () => {
    await pick();
    const request = await sent();

    expect(request.request.body).toEqual({ content: CONFIG, serviceName: 'portal' });
    await fixture.whenStable();
    expect(button().disabled).toBe(true);

    request.flush({
      services: [imported('gateway')],
      pipeline: { type: 'FULL', agentLabels: ['linux-agent'] },
      notes: [{ field: 'pipeline.cron', message: 'not imported: the portal has no such setting' }],
    });
    await fixture.whenStable();

    expect(emitted).toEqual([
      {
        file: 'config.yaml',
        name: 'gateway',
        service: imported('gateway').service,
        notes: [
          { field: 'pipeline.cron', message: 'not imported: the portal has no such setting' },
          { field: 'gateway.tools.x', message: 'check it' },
        ],
        agentLabels: ['linux-agent'],
      },
    ]);
    expect(button().disabled).toBe(false);
  });

  it('asks which service to import when the file describes several, and imports none on cancel', async () => {
    const services = [imported('gateway'), imported('worker')];
    const open = vi
      .spyOn(TestBed.inject(Dialog), 'open')
      .mockReturnValueOnce({ closed: of(services[1]) } as unknown as DialogRef<unknown>)
      .mockReturnValueOnce({ closed: of(undefined) } as unknown as DialogRef<unknown>);

    await pick();
    await answer({ services });

    expect(open).toHaveBeenCalledWith(ConfigServicePicker, { data: services });
    expect(emitted.map((config) => [config.name, config.agentLabels])).toEqual([['worker', null]]);

    await pick();
    await answer({ services });

    expect(emitted).toHaveLength(1);
  });

  it('refuses a file larger than 256 KiB without sending it', async () => {
    await pick(upload('x'.repeat(MAX_CONFIG_BYTES + 1), 'huge.yaml'));

    http.expectNone(URL);
    expect(error).toHaveBeenCalledWith('huge.yaml is larger than 256 KiB and cannot be imported.');
  });

  it('says why the file could not be imported', async () => {
    await pick();
    (await sent()).flush(
      {
        detail: 'The request has invalid values',
        errors: [{ field: 'content', message: 'is not valid YAML: line 3, column 7' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(error).toHaveBeenLastCalledWith(
      'config.yaml could not be imported. Is not valid YAML: line 3, column 7.',
    );

    await pick();
    await answer({ services: [] });

    expect(error).toHaveBeenLastCalledWith(
      'config.yaml could not be imported. The file describes no service.',
    );
    expect(emitted).toEqual([]);
  });

  it('the input takes several YAML or JSON files and names both file names', () => {
    const help = button().nextElementSibling as HTMLElement;

    expect(fileInput().multiple).toBe(true);
    expect(fileInput().accept).toBe('.yaml,.yml,.json');
    expect(fileInput().getAttribute('aria-label')).toBe(
      'devsecops_config.yaml or config.yaml to import',
    );
    expect(help.tagName).toBe('DSO-HELP');
    expect(help.querySelector('button')!.getAttribute('aria-label')).toBe(
      'More information about Import devsecops_config.yaml',
    );
    expect(helpOf(help)).toBe(
      'Reads a devsecops_config.yaml, a config.yaml (its former name), a configuration the portal rendered ' +
        'or its JSON form. Choose several files, for example both files of a repository, and the import ' +
        'reads devsecops_config.yaml, else config.yaml, as the DevSecOps library does.',
    );
  });

  it('imports devsecops_config.yaml from several files and notes that config.yaml was not read', async () => {
    await pick(
      upload('projects: {old: {}}', 'config.yaml'),
      upload(CONFIG, 'devsecops_config.yaml'),
    );
    const request = await sent();

    expect(request.request.body).toEqual({ content: CONFIG, serviceName: 'portal' });

    request.flush({ services: [imported('gateway', [])], pipeline: null, notes: [] });
    await fixture.whenStable();

    expect(emitted[0].file).toBe('devsecops_config.yaml');
    expect(emitted[0].notes[0]).toEqual({
      field: '',
      message:
        'check: the import did not read config.yaml: the DevSecOps library reads devsecops_config.yaml ' +
        'when a repository has both',
    });
    expect(noteGroups(emitted[0].notes)).toEqual([
      {
        title: 'Check',
        notes: [
          {
            field: '',
            message:
              'The import did not read config.yaml: the DevSecOps library reads devsecops_config.yaml ' +
              'when a repository has both',
          },
        ],
      },
    ]);
  });

  it('notes a skipped file of another name without the library order', async () => {
    await pick(upload(CONFIG, 'config.yaml'), upload('x', 'README.md'));
    await answer({ services: [imported('gateway', [])] });

    expect(emitted[0].file).toBe('config.yaml');
    expect(emitted[0].notes).toEqual([
      { field: '', message: 'check: the import did not read README.md' },
    ]);
  });

  it('refuses several files without either name and sends nothing', async () => {
    await pick(upload(CONFIG, 'cert-gui-full.yaml'), upload(CONFIG, 'Config.yaml'));

    http.expectNone(URL);
    expect(error).toHaveBeenCalledWith(
      'None of the chosen files is named devsecops_config.yaml or config.yaml. ' +
        'Choose one of them, or a single file of any name.',
    );
    expect(emitted).toEqual([]);
  });

  it('checks the size of the chosen file only', async () => {
    const huge = (name: string) => upload('x'.repeat(MAX_CONFIG_BYTES + 1), name);

    await pick(huge('config.yaml'), upload(CONFIG, 'devsecops_config.yaml'));
    await answer({ services: [imported('gateway')] });

    expect(emitted.map((config) => config.file)).toEqual(['devsecops_config.yaml']);

    await pick(upload(CONFIG, 'config.yaml'), huge('devsecops_config.yaml'));

    http.expectNone(URL);
    expect(error).toHaveBeenCalledWith(
      'devsecops_config.yaml is larger than 256 KiB and cannot be imported.',
    );
  });

  it('is disabled while the form cannot take another service', async () => {
    fixture.componentRef.setInput('disabled', true);
    await fixture.whenStable();

    expect(button().disabled).toBe(true);
  });
});

describe('ImportNotes', () => {
  const config = (notes: ImportedConfig['notes']): ImportedConfig => ({
    file: 'config.yaml',
    name: 'gateway',
    service: {},
    notes,
    agentLabels: null,
  });

  it('separates the settings it did not import from those to check', () => {
    expect(
      noteGroups([
        { field: 'tools.sonar.foo', message: 'not imported: the portal has no such setting' },
        { field: 'tools.build.dir', message: 'the path is outside the repository' },
        { field: 'pipeline.cron', message: 'Not imported, schedules are set in Jenkins' },
        {
          field: 'deploy.openshift.rd.buildTag',
          message: 'check: a run that builds the image replaces it',
        },
      ]),
    ).toEqual([
      {
        title: 'Not imported',
        notes: [
          { field: 'tools.sonar.foo', message: 'The portal has no such setting' },
          { field: 'pipeline.cron', message: 'Schedules are set in Jenkins' },
        ],
      },
      {
        title: 'Check',
        notes: [
          { field: 'tools.build.dir', message: 'The path is outside the repository' },
          {
            field: 'deploy.openshift.rd.buildTag',
            message: 'A run that builds the image replaces it',
          },
        ],
      },
    ]);
  });

  it('says when every setting was imported and can be dismissed', async () => {
    const fixture = TestBed.createComponent(ImportNotes);
    fixture.componentRef.setInput('imported', config([]));
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(text(page.querySelector('.banner-text'))).toBe(
      'gateway was imported from config.yaml. Every setting of the file was imported.',
    );

    buttonOf(page, 'Dismiss').click();

    expect(closed).toHaveBeenCalledOnce();
  });
});

describe('the import into a service form', () => {
  const file: ImportedConfig = {
    file: 'config.yaml',
    name: 'gateway',
    service: { ...service(), id: 4 },
    notes: [],
    agentLabels: ['docker-agent', 'linux-agent'],
  };

  it('starts a new service named as typed, or else as in the file', () => {
    const form = importedServiceForm(file, null);

    expect(form.controls.id.value).toBeNull();
    expect(form.controls.name.value).toBe('gateway');
    expect(form.controls.build.controls.javaPath.value).toBe(service().build.javaPath);
    expect(importedServiceForm(file, null, ' portal ').controls.name.value).toBe('portal');
  });

  it('fills the Node.js build and the shared version catalogs of the file', () => {
    const node = nodeBuild({ enabled: true, directory: 'frontend', commands: ['npm ci'] });
    const form = importedServiceForm(
      {
        ...file,
        service: {
          ...file.service,
          nodeBuild: node,
          versionCatalogs: ['gradle/libs.versions.toml'],
        },
      },
      null,
    );

    expect(toServiceRequest(form)).toMatchObject({
      nodeBuild: node,
      versionCatalogs: ['gradle/libs.versions.toml'],
    });
  });

  it('fills the Jenkins agents with those of the file until they are changed by hand', () => {
    const agents = new FormControl('linux-agent', { nonNullable: true });

    prefillAgents(agents, file.agentLabels);

    expect(agents.value).toBe('docker-agent, linux-agent');

    agents.setValue('mac-agent');
    agents.markAsDirty();
    prefillAgents(agents, ['docker-agent']);

    expect(agents.value).toBe('mac-agent');

    const stored = new FormControl('', { nonNullable: true });
    stored.disable();
    prefillAgents(stored, ['docker-agent']);

    expect(stored.value).toBe('');
  });
});
