import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { FormControl } from '@angular/forms';
import { buttonOf, choose, optionsOf, selectOf, text } from '@common/testing/dom';
import {
  anotherService,
  globalSettings,
  nodeBuild,
  service,
  serviceTemplate,
} from '../testing/fixtures';
import {
  createServiceForm,
  createUrbanCodeApplicationForm,
  toServiceRequest,
} from '../products/product-form-model';
import { WizardDefaults, WizardService, imageRepository } from './self-service-model';
import { ServiceDialog, ServiceDialogData } from './service-dialog';

const APP_ID = '7d1f3a52-9c4b-4e8a-b2d6-0f5e1c9a8b31';
const REPOSITORY = 'https://bitbucket.bbh.com/projects/PAY/repos/gateway';
const BBH_DEFAULTS: WizardDefaults = {
  tool: 'GRADLE',
  target: 'VM',
  template: serviceTemplate(),
  productCode: 'PAY',
  settings: null,
};

describe('ServiceDialog', () => {
  let fixture: ComponentFixture<ServiceDialog>;
  let component: ServiceDialog;
  const close = vi.fn();

  async function open(data: Partial<ServiceDialogData> = {}) {
    TestBed.configureTestingModule({
      imports: [ServiceDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DialogRef, useValue: { close } },
        {
          provide: DIALOG_DATA,
          useValue: {
            pipeline: 'FULL',
            service: null,
            takenNames: ['gui'],
            defaults: BBH_DEFAULTS,
            ...data,
          },
        },
      ],
    });
    fixture = TestBed.createComponent(ServiceDialog);
    component = fixture.componentInstance;
    await fixture.whenStable();
  }

  const form = () => component['serviceForm']();
  const page = () => fixture.nativeElement as HTMLElement;
  const submit = () => buttonOf(page(), 'Add service');
  const marked = () => [...page().querySelectorAll('.rail-item.problem')].map(text);

  function fillIn(name = 'new-service') {
    form().patchValue({
      name,
      appScan: { applicationId: APP_ID, keyId: 'bbh_key' },
      scm: { repositoryUrl: REPOSITORY },
    });
  }

  afterEach(() => close.mockReset());

  it('refuses duplicate service name', async () => {
    await open({ pipeline: 'FULL', takenNames: ['backend-api'] });
    form().controls.name.setValue('backend-api');

    expect(form().controls.name.errors).toEqual({
      rule: 'Another service of this product already has this name',
    });
  });

  it('keeps Add service disabled and names the sections that need input until the form is complete', async () => {
    await open({ pipeline: 'SECURITY' });

    expect(submit().disabled).toBe(true);
    expect(text(page().querySelector('.missing'))).toBe(
      'Complete these sections first: General, AppScan SAST and DAST.',
    );
    expect(marked()).toEqual(['General', 'AppScan SAST and DAST']);

    fillIn();
    await fixture.whenStable();

    expect(submit().disabled).toBe(false);
    expect(page().querySelector('.missing')).toBeNull();
    expect(marked()).toEqual([]);

    submit().click();

    expect(close).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'new-service',
        appScanId: APP_ID,
        repositoryUrl: REPOSITORY,
      }),
    );
  });

  it('keeps Add service disabled while a Node.js build field is invalid and opens Build on save', async () => {
    await open({ pipeline: 'SECURITY' });
    fillIn();
    form().controls.nodeBuild.patchValue({
      enabled: true,
      installationName: 'NodeJS-20',
      nodePath: '/opt/node-20',
    });
    await fixture.whenStable();

    expect(submit().disabled).toBe(true);
    expect(text(page().querySelector('.missing'))).toBe('Complete these sections first: Build.');
    expect(marked()).toEqual(['Build']);

    component['save']();
    await fixture.whenStable();

    expect(close).not.toHaveBeenCalled();
    expect(text(page().querySelector('.rail-item.active span'))).toBe('Build');

    form().controls.nodeBuild.controls.nodePath.setValue('');
    await fixture.whenStable();

    expect(submit().disabled).toBe(false);
  });

  it('asks for an UrbanCode Deploy application for DV before adding a service with a Full pipeline', async () => {
    await open({ pipeline: 'FULL' });
    fillIn('archive-api');
    await fixture.whenStable();

    expect(submit().disabled).toBe(true);
    expect(marked()).toEqual(['UrbanCode Deploy']);

    form().controls.urbanCodeApplications.push(
      createUrbanCodeApplicationForm({ ...service().urbanCodeApplications[0], environments: [] }),
    );
    await fixture.whenStable();
    submit().click();

    expect(close).toHaveBeenCalledOnce();
  });

  it('asks for the AppScan key of a service with a Nexus IQ pipeline, as every pipeline signs in with it', async () => {
    await open({ pipeline: 'NEXUS_IQ' });

    expect(marked()).toContain('AppScan SAST and DAST');
  });

  it.each([
    ['SAST', 'VM'],
    ['NEXUS_IQ', 'VM'],
    ['SECURITY', 'OPENSHIFT'],
    ['FULL', 'OPENSHIFT'],
  ] as const)(
    'starts a new service of the %s pipeline on %s when OpenShift is the default',
    async (pipeline, target) => {
      await open({ pipeline, defaults: { ...BBH_DEFAULTS, target: 'OPENSHIFT' } });

      expect(form().controls.deployment.controls.target.value).toBe(target);
    },
  );

  it('starts a new service with the Library defaults and the AppScan key of the product', async () => {
    await open({
      defaults: { ...BBH_DEFAULTS, settings: globalSettings() },
      sources: [{ name: 'gui', request: service() }],
    });
    const c = form().controls;

    expect(c.build.controls.sourceDir.value).toBe('app');
    expect(c.sonar.controls.installationName.value).toBe('SonarQube');
    expect(c.appScan.controls.keyId.value).toBe('bbh_key');
  });

  it('starts without Library defaults while the global settings are not loaded', async () => {
    await open();

    expect(form().controls.sonar.controls.installationName.value).toBe('');
  });

  it('copies the values of another service except those that identify it, keeping the typed name', async () => {
    await open({
      sources: [
        {
          name: 'gui',
          request: service({
            nodeBuild: nodeBuild({ enabled: true, directory: 'frontend' }),
          }),
        },
        { name: 'backend-api', request: anotherService({ name: 'backend-api' }) },
      ],
    });
    const select = selectOf(page(), 'Copy values from');
    form().controls.name.setValue('portal');

    expect(optionsOf(select)).toEqual(['No service', 'gui', 'backend-api']);

    choose(select, 'gui');
    await fixture.whenStable();
    const value = form().getRawValue();

    expect(value.name).toBe('portal');
    expect(value.build.javaPath).toBe('/usr/lib/jvm/java-17-openjdk');
    expect(value.sonar.projectKey).toBe('');
    expect(value.sonar.projectName).toBe('');
    expect(value.metrics.influxProject).toBe('');
    expect(value.nexusIqApplications.map((app) => app.application)).toEqual(['pay-portal']);
    expect(value.nodeBuild).toMatchObject({ enabled: true, directory: 'frontend' });
  });

  it('sets the OpenShift names of a copy from the typed name, as for a new service', async () => {
    const image = (name: string) => imageRepository('pay', name, serviceTemplate());
    const gui = createServiceForm(
      service({ appScan: { ...service().appScan, scanName: 'gui-scan' } }),
    );
    gui.patchValue({
      deployment: { target: 'OPENSHIFT', appName: 'gui', artifactName: 'gui.jar' },
      openShiftTargets: {
        RD: {
          projectBuild: 'pay-build',
          dockerRepoPush: image('gui'),
          dockerRepoPull: image('gui'),
        },
        QC: { dockerRepoPull: image('gui') },
      },
    });
    await open({ sources: [{ name: 'gui', request: toServiceRequest(gui) }] });
    form().controls.name.setValue('portal');

    choose(selectOf(page(), 'Copy values from'), 'gui');
    await fixture.whenStable();
    const value = form().getRawValue();

    expect(value.deployment.appName).toBe('portal');
    expect(value.deployment.artifactName).toBe('gui.jar');
    expect(value.appScan.scanName).toBe('');
    expect([
      value.openShiftTargets.RD.dockerRepoPush,
      value.openShiftTargets.RD.dockerRepoPull,
      value.openShiftTargets.QC.dockerRepoPull,
    ]).toEqual([image('portal'), image('portal'), image('portal')]);

    form().controls.name.setValue('portal-api');

    expect(form().controls.deployment.controls.appName.value).toBe('portal-api');
    expect(form().controls.openShiftTargets.controls.RD.controls.dockerRepoPush.value).toBe(
      image('portal-api'),
    );
  });

  it('starts afresh with No service, keeping the typed name, and drops the import notes on a copy', async () => {
    await open({ sources: [{ name: 'gui', request: service() }] });
    component['importConfig']({
      file: 'config.yaml',
      name: 'gateway',
      service: anotherService(),
      notes: [],
      agentLabels: [],
    });
    form().controls.name.setValue('portal');
    await fixture.whenStable();
    const select = selectOf(page(), 'Copy values from');

    expect(page().querySelector('dso-import-notes')).not.toBeNull();

    choose(select, 'gui');
    await fixture.whenStable();

    expect(page().querySelector('dso-import-notes')).toBeNull();
    expect(form().controls.build.controls.javaPath.value).toBe('/usr/lib/jvm/java-17-openjdk');

    choose(select, 'No service');
    await fixture.whenStable();

    expect(form().controls.name.value).toBe('portal');
    expect(form().controls.build.controls.javaPath.value).toBe('');
    expect(form().controls.sonar.controls.projectKey.value).toBe('');
  });

  it('shows a service added in this wizard with its Library defaults again when it is changed', async () => {
    const request = toServiceRequest(createServiceForm(service()));
    const added = (id: number | null): WizardService => ({
      id,
      name: 'gui',
      description: '',
      appScanId: APP_ID,
      tool: 'GRADLE',
      target: 'VM',
      openShiftProject: '',
      nexusIqApplication: 'pay-gui',
      repositoryUrl: REPOSITORY,
      serviceRequest: { ...request, id },
    });
    await open({ service: added(null), defaults: { ...BBH_DEFAULTS, settings: globalSettings() } });

    expect(form().controls.sonar.controls.installationName.value).toBe('SonarQube');

    TestBed.resetTestingModule();
    await open({ service: added(10), defaults: { ...BBH_DEFAULTS, settings: globalSettings() } });

    expect(form().controls.sonar.controls.installationName.value).toBe('');
  });

  it('fills the form from an imported config.yaml, keeping the typed name and the agents typed before', async () => {
    const agents = new FormControl('linux-agent', { nonNullable: true });
    await open({ agents });
    form().controls.name.setValue('portal');

    component['importConfig']({
      file: 'config.yaml',
      name: 'gateway',
      service: { ...service(), id: 4 },
      notes: [
        { field: 'tools.sonar.foo', message: 'not imported: the portal has no such setting' },
      ],
      agentLabels: ['docker-agent'],
    });
    await fixture.whenStable();

    expect(form().controls.name.value).toBe('portal');
    expect(form().controls.id.value).toBeNull();
    expect(form().controls.build.controls.javaPath.value).toBe('/usr/lib/jvm/java-17-openjdk');
    expect(agents.value).toBe('docker-agent');
    const notes = page().querySelector('dso-import-notes')!;
    expect(text(notes.querySelector('strong'))).toBe('gateway was imported from config.yaml.');
    expect(text(notes.querySelector('h5'))).toBe('Not imported');
    expect(text(notes.querySelector('li'))).toBe('tools.sonar.foo: The portal has no such setting');

    agents.markAsDirty();
    component['importConfig']({
      file: 'config.yaml',
      name: 'gateway',
      service: service(),
      notes: [],
      agentLabels: ['mac-agent'],
    });

    expect(agents.value).toBe('docker-agent');
  });

  it('loads existing service settings into form', async () => {
    const existing: WizardService = {
      id: 10,
      name: 'existing-service',
      description: 'Some desc',
      appScanId: APP_ID,
      tool: 'MAVEN',
      target: 'OPENSHIFT',
      openShiftProject: 'pay-hub',
      nexusIqApplication: 'pay-app',
      repositoryUrl: REPOSITORY,
    };
    await open({ service: existing });

    expect(form().controls.name.value).toBe('existing-service');
    expect(form().controls.build.controls.tool.value).toBe('MAVEN');
    expect(form().controls.deployment.controls.target.value).toBe('OPENSHIFT');
    expect(selectOf(page(), 'Copy values from')).toBeUndefined();
  });
});
