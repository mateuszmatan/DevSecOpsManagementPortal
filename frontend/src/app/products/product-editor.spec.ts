import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ConfirmDialog, ConfirmDialogData } from '@common/shared/confirm-dialog';
import {
  anotherService,
  department,
  globalSettings,
  nodeBuild,
  product,
  service,
  servicePipelines,
  serviceTemplate,
} from '../testing/fixtures';
import {
  buttonOf,
  fieldOf,
  helpOf,
  inputOf,
  selectOf,
  text,
  unexplained,
} from '@common/testing/dom';
import { GeneratedKeys } from './generated-keys';
import { createUrbanCodeApplicationForm, toServiceRequest } from './product-form-model';
import { ProductEditor } from './product-editor';
import { ProductNameDialog } from './product-name-dialog';

describe('ProductEditor', () => {
  let fixture: ComponentFixture<ProductEditor>;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductEditor],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(ProductEditor);
  });

  afterEach(() => http.verify());

  const editor = () => fixture.componentInstance;
  const template = () =>
    http
      .expectOne('/api/service-template')
      .flush(serviceTemplate({ mavenTasks: 'clean install', deliveryTasks: 'deploy' }));
  const page = () => fixture.nativeElement as HTMLElement;

  const closing = (result: unknown) =>
    vi
      .spyOn(TestBed.inject(Dialog), 'open')
      .mockReturnValue({ closed: of(result) } as unknown as DialogRef<unknown>);
  const naming = (name: string | undefined, departmentId = 3) =>
    closing(name && { name, departmentId });
  const departments = [department(), department({ id: 5, name: 'Fund Services' })];

  const suggestion = (name: string) =>
    http.expectOne(
      (request) =>
        request.url === '/api/products/code-suggestion' && request.params.get('name') === name,
    );

  async function start(name = 'CertScanner', code = 'CERTSCANNER', settings = globalSettings()) {
    naming(name);
    await fixture.whenStable();
    http.expectOne('/api/departments').flush(departments);
    http.expectOne('/api/settings').flush(settings);
    template();
    suggestion(name).flush({ code });
    await fixture.whenStable();
  }

  async function rename(name: string) {
    editor()['form'].controls.name.setValue(name);
    await new Promise((resolve) => setTimeout(resolve, 350));
  }

  async function submit() {
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  async function edit(services = [service(), anotherService()], stored = product({ services })) {
    fixture.componentRef.setInput('id', '1');
    await fixture.whenStable();
    http.expectOne('/api/products/1').flush(stored);
    http
      .expectOne('/api/products/1/pipelines')
      .flush([
        servicePipelines(),
        servicePipelines({ serviceId: 11, serviceName: 'api', pipelines: [] }),
      ]);
    http.expectOne('/api/settings').flush(globalSettings());
    template();
    http.expectOne('/api/departments').flush(departments);
    await fixture.whenStable();
  }

  const names = () =>
    [...page().querySelectorAll('dso-panel .service-name')].map((name) => name.textContent?.trim());
  const panels = () => [...page().querySelectorAll<HTMLElement>('dso-panel.service-panel')];
  const expandedPanels = () => panels().filter((panel) => panel.classList.contains('expanded'));

  const imageBuild = {
    RD: {
      projectBuild: 'cert-build',
      buildConfigPath: 'openshift/buildconfig.yaml',
      dockerFilePath: 'openshift/Dockerfile',
      buildContext: 'target/docker',
      dockerRepoPush: 'nexus.bbh.com:18444/cert',
      nexusAuthFile: '/etc/containers/auth.json',
    },
  };

  function fillValidProduct() {
    editor()['form'].patchValue({
      code: 'CERT',
      name: 'CertScanner',
    });
    editor()
      ['form'].controls.services.at(0)
      .patchValue({
        name: 'gui',
        build: { javaPath: '/usr/lib/jvm/java-17-openjdk', command: { tasks: 'clean package' } },
        deployment: { appName: 'gui', artifactName: 'gui.jar' },
        openShiftTargets: imageBuild,
        appScan: { applicationId: '109f44ac-cc06-4ca0-884e-d944904f7019', keyId: 'bbh_key' },
      });
    editor()['form'].controls.services.at(0).markAsDirty();
  }

  it('starts a new product with one open service', async () => {
    await start();

    expect(page().querySelector('h1')?.textContent).toBe('Add product');
    expect(panels().length).toBe(1);
    expect(editor()['expanded']()).toBe(0);
    expect(text(expandedPanels()[0].querySelector('.panel-toggle'))).toBe('Close');
    expect(editor().hasUnsavedChanges()).toBe(false);
  });

  it('fills the build of an added service from the service template', async () => {
    await start();
    editor()['addService']();

    const added = editor()['form'].controls.services.at(1).getRawValue();
    expect(added.build.buildPath).toBe('target/*.jar');
    expect(added.build.command.tasks).toBe('clean install');
    expect(added.delivery.tasks).toBe('deploy');
  });

  it('swaps the template build of a new service for the one of its new build tool', async () => {
    await start();
    const build = editor()['form'].controls.services.at(0).controls.build.controls;
    const values = () => [build.command.controls.tasks.value, build.buildPath.value];
    expect(values()).toEqual(['clean install', 'target/*.jar']);

    build.tool.setValue('GRADLE');
    expect(values()).toEqual(['clean build', 'build/libs/*.jar']);

    build.command.controls.tasks.setValue('clean assemble');
    build.tool.setValue('MAVEN');
    expect(values()).toEqual(['clean assemble', 'target/*.jar']);

    build.tool.setValue('FLUTTER');
    build.tool.setValue('GRADLE');
    expect(values()).toEqual(['clean assemble', 'build/libs/*.jar']);
  });

  it('starts a new service with the build tool set up automatically, so it needs no JDK path', async () => {
    await start();
    const build = editor()['form'].controls.services.at(0).controls.build.controls;

    expect(build.autoSetup.value).toBe(true);
    expect(build.javaPath.valid).toBe(true);
  });

  it('starts a new Flutter service of the Library defaults without automatic setup, which Flutter cannot use', async () => {
    const settings = globalSettings();
    await start('CertScanner', 'CERTSCANNER', {
      ...settings,
      serviceDefaults: { ...settings.serviceDefaults, buildTool: 'FLUTTER' },
    });
    editor()['addService']();

    for (const service of editor()['form'].controls.services.controls) {
      expect(service.controls.build.controls.tool.value).toBe('FLUTTER');
      expect(service.controls.build.controls.autoSetup.value).toBe(false);
      expect(toServiceRequest(service).build.autoSetup).toBe(false);
    }

    const build = editor()['form'].controls.services.at(1).controls.build.controls;
    build.tool.setValue('GRADLE');

    expect(build.autoSetup.value).toBe(true);
    expect(build.javaPath.valid).toBe(true);
  });

  it('offers the Nexus IQ application of the service template once, so one that was removed stays away', async () => {
    await start();
    const gui = editor()['form'].controls.services.at(0);
    gui.controls.name.setValue('gui');
    expect(gui.controls.nexusIqApplications.length).toBe(1);

    gui.controls.nexusIqApplications.removeAt(0);
    gui.controls.name.setValue('gui2');

    expect(gui.controls.nexusIqApplications.length).toBe(0);
    expect(gui.controls.scm.controls.repositoryUrl.value).toBe(
      'https://bitbucket.bbh.com/projects/CERTSCANNER/repos/certscanner-gui2',
    );
  });

  it('fills what a new service takes from its name and the product code from the service template', async () => {
    await start();
    const gui = editor()['form'].controls.services.at(0);
    const named = () => {
      const v = gui.getRawValue();
      return [
        v.scm.repositoryUrl,
        v.scm.credentialsId,
        v.deployment.appName,
        v.nexusIqApplications,
      ];
    };
    const application = (name: string, scanPatterns: string) => [
      { application: name, scanPatterns, stage: 'build', failOnNetworkError: false },
    ];

    gui.controls.name.setValue('gui');

    expect(named()).toEqual([
      'https://bitbucket.bbh.com/projects/CERTSCANNER/repos/certscanner-gui',
      'bitbucket-http-credentials',
      'gui',
      application('certscanner-gui', '**/target/*.jar'),
    ]);

    editor()['form'].controls.code.setValue('cert');
    gui.controls.build.controls.tool.setValue('GRADLE');

    expect(named()).toEqual([
      'https://bitbucket.bbh.com/projects/CERT/repos/cert-gui',
      'bitbucket-http-credentials',
      'gui',
      application('cert-gui', '**/build/libs/*.jar'),
    ]);
  });

  it('keeps the repository and the Nexus IQ application of a stored service that is renamed', async () => {
    await edit();
    const gui = editor()['form'].controls.services.at(0);
    const { scm, nexusIqApplications } = gui.getRawValue();

    gui.controls.name.setValue('portal');

    expect(gui.getRawValue()).toMatchObject({ scm, nexusIqApplications });
  });

  it('keeps the build of a stored service when its build tool changes', async () => {
    await edit();
    const build = editor()['form'].controls.services.at(0).controls.build.controls;
    const stored = [build.command.controls.tasks.value, build.buildPath.value];

    build.tool.setValue(build.tool.value === 'MAVEN' ? 'GRADLE' : 'MAVEN');

    expect([build.command.controls.tasks.value, build.buildPath.value]).toEqual(stored);
  });

  it('asks for the department and the name first and makes the unique code from the name', async () => {
    const open = naming('Payments Hub', 5);
    fixture.componentRef.setInput('department', '5');
    await fixture.whenStable();
    http.expectOne('/api/departments').flush(departments);
    http.expectOne('/api/settings').flush(globalSettings());
    template();
    suggestion('Payments Hub').flush({ code: 'PAYMENTSHUB2' });
    await fixture.whenStable();

    expect(open).toHaveBeenCalledWith(ProductNameDialog, {
      data: { departments, departmentId: 5 },
    });
    expect(inputOf(page(), 'Name').value).toBe('Payments Hub');
    expect(inputOf(page(), 'Code').value).toBe('PAYMENTSHUB2');
    expect(editor()['form'].controls.departmentId.value).toBe(5);
    expect(
      text(
        selectOf(page().querySelector('.product-fields')!, 'Business Service').selectedOptions[0],
      ),
    ).toBe('Fund Services');
    expect(editor().hasUnsavedChanges()).toBe(false);
  });

  it('goes back to the products when the name is not given', async () => {
    const open = naming(undefined);
    await fixture.whenStable();
    http.expectOne('/api/departments').flush(departments);

    expect(open.mock.calls[0][1]?.data).toEqual({ departments, departmentId: null });
    expect(router.navigate).toHaveBeenCalledWith(['/admin/products']);
  });

  it('says why a new product cannot start without the departments and loads them again', async () => {
    const open = naming(undefined);
    await fixture.whenStable();
    http
      .expectOne('/api/departments')
      .flush({ detail: 'The database is not available' }, { status: 500, statusText: 'Error' });
    await fixture.whenStable();

    expect(open).not.toHaveBeenCalled();
    expect(text(page().querySelector('.banner .banner-text'))).toBe(
      'The business services could not be loaded. The database is not available',
    );
    expect(page().querySelector('dso-load-error a')?.getAttribute('href')).toBe('/admin/products');

    buttonOf(page().querySelector('dso-load-error')!, 'Try again').click();
    await fixture.whenStable();
    http.expectOne('/api/departments').flush(departments);
    await fixture.whenStable();

    expect(page().querySelector('dso-load-error')).toBeNull();
    expect(open).toHaveBeenCalledOnce();
  });

  it('says why a stored product could not be loaded', async () => {
    fixture.componentRef.setInput('id', '1');
    await fixture.whenStable();
    http.expectOne('/api/products/1/pipelines').flush([]);
    http.expectOne('/api/settings').flush(globalSettings());
    template();
    http.expectOne('/api/departments').flush(departments);
    http.expectOne('/api/products/1').flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(text(page().querySelector('.banner .banner-text'))).toBe(
      'The product could not be loaded. The portal could not complete the request (error 404 Not Found). ' +
        'Try again in a moment; if it keeps failing, tell the portal administrator.',
    );
    expect(page().querySelector('form')).toBeNull();
  });

  it('keeps the code in step with the name until the code is changed by hand', async () => {
    await start();
    await rename('Cert Scanner Next');
    suggestion('Cert Scanner Next').flush({ code: 'CERTSCANNERNEXT' });
    await fixture.whenStable();

    expect(editor()['form'].controls.code.value).toBe('CERTSCANNERNEXT');

    editor()['form'].controls.code.setValue('CERTNEXT');
    await rename('Cert Scanner Two');

    http.expectNone((request) => request.url === '/api/products/code-suggestion');
    expect(editor()['form'].controls.code.value).toBe('CERTNEXT');
  });

  it('adds the product and opens it', async () => {
    await start();
    fillValidProduct();
    await submit();

    const request = http.expectOne({ method: 'POST', url: '/api/products' });
    expect(request.request.body).toMatchObject({
      code: 'CERT',
      departmentId: 3,
      version: null,
      services: [{ id: null, name: 'gui' }],
    });
    request.flush(product({ id: 5 }));
    await fixture.whenStable();

    expect(router.navigate).toHaveBeenCalledWith(['/admin/products', 5]);
    expect(editor().hasUnsavedChanges()).toBe(false);
    expect(TestBed.inject(GeneratedKeys).take(5)).toEqual(['gui']);
  });

  it('marks the fields the API refused and lists the problems without a field', async () => {
    await start();
    editor()['addService']();
    fillValidProduct();
    editor()
      ['form'].controls.services.at(1)
      .patchValue({
        name: 'api',
        build: { javaPath: '/usr/lib/jvm/java-21-openjdk', command: { tasks: 'clean package' } },
        deployment: { appName: 'api', artifactName: 'api.jar' },
        openShiftTargets: imageBuild,
        appScan: { applicationId: '209f44ac-cc06-4ca0-884e-d944904f7019', keyId: 'bbh_key' },
      });
    await submit();

    http.expectOne('/api/products').flush(
      {
        detail: 'The request has invalid values',
        errors: [
          { field: 'services[1].sonar.projectKey', message: 'is already used by PAYHUB/gateway' },
          { field: 'serviceTemplate', message: 'is not configured' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(
      editor()['form'].controls.services.at(1).controls.sonar.controls.projectKey.errors,
    ).toEqual({
      server: 'is already used by PAYHUB/gateway',
    });
    expect(editor()['expanded']()).toBe(1);
    expect(page().querySelector('.problems')?.textContent).toContain(
      'serviceTemplate: is not configured',
    );
    expect(page().querySelector('.dso-panel.expanded .rail-item.active')?.textContent).toContain(
      'SonarQube',
    );
  });

  it('asks for an UrbanCode Deploy application for DV before adding a service on virtual machines', async () => {
    await start();
    fillValidProduct();
    const gui = editor()['form'].controls.services.at(0);
    gui.controls.deployment.controls.target.setValue('VM');
    await submit();

    http.expectNone('/api/products');
    expect(text(page().querySelector('.save-error'))).toBe('Some fields need your attention.');
    expect(text(panels()[0].querySelector('.tag.error'))).toBe('Needs attention');
    expect(text(page().querySelector('.dso-panel.expanded .list-error'))).toBe(
      'Add an UrbanCode Deploy application for DV: the Full pipeline uses it to deploy to the lower test region',
    );

    gui.controls.urbanCodeApplications.push(
      createUrbanCodeApplicationForm(service().urbanCodeApplications[0]),
    );
    await submit();

    const request = http.expectOne({ method: 'POST', url: '/api/products' });
    expect(request.request.body.services[0].urbanCodeApplications[0].environments).toEqual([
      'DV',
      'RD',
    ]);
    request.flush(product({ id: 5 }));
    await fixture.whenStable();
  });

  it('asks for an UrbanCode Deploy application for DV only for stored services with a Full pipeline', async () => {
    await edit([
      service({ urbanCodeApplications: [] }),
      anotherService({ urbanCodeApplications: [] }),
    ]);
    await submit();

    http.expectNone({ method: 'PUT', url: '/api/products/1' });
    expect(panels().map((panel) => text(panel.querySelector('.tag.error')))).toEqual([
      'Needs attention',
      '',
    ]);

    editor()
      ['form'].controls.services.at(0)
      .controls.urbanCodeApplications.push(
        createUrbanCodeApplicationForm({ ...service().urbanCodeApplications[0], environments: [] }),
      );
    await submit();

    http.expectOne({ method: 'PUT', url: '/api/products/1' }).flush(product());
    await fixture.whenStable();
  });

  it('shows why the portal refused the product', async () => {
    await edit();
    await submit();

    const refusal =
      'Service gui deploys to virtual machines but has no UrbanCode Deploy application for DV, ' +
      'so its Full pipeline would fail at the lower test region deployment';
    http
      .expectOne({ method: 'PUT', url: '/api/products/1' })
      .flush({ detail: refusal }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(text(page().querySelector('.save-error'))).toBe(
      `The product could not be saved. ${refusal}`,
    );
  });

  it('refuses two services with the same name before asking the API', async () => {
    await start();
    editor()['addService']();
    fillValidProduct();
    editor()
      ['form'].controls.services.at(1)
      .patchValue({
        name: 'gui',
        build: { javaPath: '/usr/lib/jvm/java-21-openjdk', command: { tasks: 'clean package' } },
        deployment: { appName: 'api', artifactName: 'api.jar' },
        openShiftTargets: imageBuild,
        appScan: { applicationId: '209f44ac-cc06-4ca0-884e-d944904f7019', keyId: 'bbh_key' },
      });
    await submit();

    http.expectNone('/api/products');
    expect(editor()['form'].controls.services.at(1).controls.name.errors).toEqual({
      rule: 'another service of this product already uses this name',
    });
    expect(editor()['expanded']()).toBe(1);
  });

  it('loads a stored product and saves it with its version', async () => {
    fixture.componentRef.setInput('id', '1');
    await fixture.whenStable();
    http
      .expectOne('/api/products/1')
      .flush(product({ services: [service(), anotherService({ name: 'api' })] }));
    http.expectOne('/api/products/1/pipelines').flush([servicePipelines()]);
    http.expectOne('/api/settings').flush(globalSettings());
    template();
    http.expectOne('/api/departments').flush(departments);
    await fixture.whenStable();

    expect(page().querySelector('h1')?.textContent).toBe('Edit CertScanner');
    expect(page().querySelector('.tag')?.textContent?.trim()).toBe('1 pipeline');

    editor()['move'](1, -1);
    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/products/1' });
    expect(request.request.body.version).toBe(3);
    expect(request.request.body.services.map((s: { name: string }) => s.name)).toEqual([
      'api',
      'gui',
    ]);
    request.flush(product());
    await fixture.whenStable();
    expect(router.navigate).toHaveBeenCalledWith(['/admin/products', 1]);
    expect(TestBed.inject(GeneratedKeys).take(1)).toEqual([]);
  });

  it('requires a department for a product that is not in one yet', async () => {
    await edit([service()], product({ departmentId: null }));

    await submit();

    http.expectNone({ method: 'PUT', url: '/api/products/1' });
    expect(page().querySelector('.save-error')?.textContent).toBe(
      'Some fields need your attention.',
    );
    expect(editor()['form'].controls.departmentId.hasError('required')).toBe(true);

    editor()['form'].controls.departmentId.setValue(5);
    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/products/1' });
    expect(request.request.body.departmentId).toBe(5);
    request.flush(product({ departmentId: 5 }));
    await fixture.whenStable();
  });

  it('moves services up and down and keeps the open one open', async () => {
    await edit();
    expect(expandedPanels()).toEqual([]);
    panels()[0].querySelector<HTMLButtonElement>('.accordion-button')!.click();
    await fixture.whenStable();
    expect(expandedPanels()).toEqual([panels()[0]]);
    panels()[1].querySelector<HTMLButtonElement>('.accordion-button')!.click();
    await fixture.whenStable();
    expect(expandedPanels()).toEqual([panels()[1]]);
    expect(editor()['expanded']()).toBe(1);
    panels()[0].querySelector<HTMLButtonElement>('.accordion-button')!.click();
    await fixture.whenStable();

    buttonOf(page(), 'Move gui down').click();
    await fixture.whenStable();
    expect(names()).toEqual(['api', 'gui']);
    expect(editor()['expanded']()).toBe(1);

    buttonOf(page(), 'Move gui up').click();
    await fixture.whenStable();
    expect(names()).toEqual(['gui', 'api']);
    expect(editor()['expanded']()).toBe(0);
    expect(buttonOf(page(), 'Move gui up').disabled).toBe(true);
    expect(editor().hasUnsavedChanges()).toBe(true);
  });

  it('removes a stored service once confirmed and sends the product without it', async () => {
    await edit();
    const open = closing(true);
    editor()['panelToggled'](1, true);

    editor()['remove'](0);
    await fixture.whenStable();

    expect((open.mock.calls[0][1]?.data as ConfirmDialogData).message).toContain(
      'Saving the product deletes',
    );
    expect(names()).toEqual(['api']);
    expect(editor()['expanded']()).toBe(0);
    await submit();
    const request = http.expectOne({ method: 'PUT', url: '/api/products/1' });
    expect(request.request.body.services.map((s: { id: number }) => s.id)).toEqual([11]);
    request.flush(product({ services: [anotherService()] }));
    await fixture.whenStable();
  });

  it('turns the product code into upper case as it is typed', async () => {
    await start();
    const input = inputOf(page(), 'Code');

    input.value = 'cert';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(input.value).toBe('CERT');
    expect(input.classList).not.toContain('uppercase');
    expect(editor()['form'].controls.code.valid).toBe(true);
  });

  it('duplicates a service right after it', async () => {
    await start();
    editor()['form'].controls.services.at(0).patchValue({ name: 'gui' });
    editor()['duplicate'](0);
    await fixture.whenStable();

    expect(editor()['form'].controls.services.controls.map((s) => s.controls.name.value)).toEqual([
      'gui',
      'gui-copy',
    ]);
    expect(editor()['expanded']()).toBe(1);
  });

  it('leaves the blank new service out of the service actions and names the services that block them', async () => {
    await start();
    const actions = () =>
      [...page().querySelectorAll<HTMLButtonElement>('.service-actions button')].map(
        (button) => `${text(button)}${button.disabled ? ' (disabled)' : ''}`,
      );
    const first = editor()['form'].controls.services.at(0);

    expect(actions()).toEqual(['Import config.yaml', 'Add service']);
    expect(page().querySelector('.blocked')).toBeNull();

    first.controls.name.setValue('gui');
    first.markAsDirty();
    await fixture.whenStable();

    expect(actions()).toEqual([
      'Copy values from existing service (disabled)',
      'Import config.yaml (disabled)',
      'Add service (disabled)',
    ]);
    expect(text(page().querySelector('.blocked'))).toBe(
      'Complete these services before adding another: gui (Artifact name, Build project, BuildConfig file, ' +
        'Dockerfile, Build context, Image pushed to, Nexus auth file, AppScan application ID, AppScan API key ID).',
    );

    buttonOf(page().querySelector('.blocked')!, 'AppScan API key ID').click();
    await fixture.whenStable();

    expect(text(expandedPanels()[0].querySelector('.rail-item.active span'))).toBe(
      'AppScan SAST and DAST',
    );
    expect(document.activeElement).toBe(inputOf(expandedPanels()[0], 'AppScan API key ID'));
    expect(
      text(fieldOf(expandedPanels()[0], 'AppScan API key ID')!.querySelector('dso-error')),
    ).toBe('Required');

    fillValidProduct();
    first.controls.appScan.patchValue({ secretCredentialsId: 'asoc-secret' });
    await fixture.whenStable();

    expect(actions()).toEqual([
      'Copy values from existing service',
      'Import config.yaml',
      'Add service',
    ]);
    expect(page().querySelector('.blocked')).toBeNull();

    buttonOf(page(), 'Add service').click();
    await fixture.whenStable();
    const added = editor()['form'].controls.services.at(1).controls.appScan.getRawValue();

    expect(panels().length).toBe(2);
    expect([added.keyId, added.secretCredentialsId]).toEqual(['bbh_key', 'asoc-secret']);
    expect(actions()).toEqual([
      'Copy values from existing service',
      'Import config.yaml',
      'Add service',
    ]);
  });

  it('fills the blank new service from an imported config.yaml and marks what it lacks', async () => {
    await start();

    editor()['importService']({
      file: 'config.yaml',
      name: 'gateway',
      service: { ...anotherService(), id: 11, nexusIqApplications: [] },
      notes: [],
      agentLabels: [],
    });
    await fixture.whenStable();
    const filled = editor()['form'].controls.services.at(0);

    expect(names()).toEqual(['gateway']);
    expect(filled.controls.id.value).toBeNull();
    expect(filled.controls.build.controls.tool.value).toBe(anotherService().build.tool);
    expect(filled.touched).toBe(true);
    expect(editor()['expanded']()).toBe(0);
  });

  it('imports a config.yaml as it is, and follows the service template only once the service is renamed', async () => {
    await start();
    const legacy = {
      ...anotherService(),
      id: null,
      name: 'legacy',
      nexusIqApplications: [],
      scm: { ...anotherService().scm, repositoryUrl: null, credentialsId: null },
    };

    editor()['importService']({
      file: 'config.yaml',
      name: 'legacy',
      service: legacy,
      notes: [],
      agentLabels: [],
    });
    editor()['form'].controls.code.setValue('PAY');
    await fixture.whenStable();
    const imported = editor()['form'].controls.services.at(0);

    expect(imported.controls.nexusIqApplications.length).toBe(0);
    expect(imported.controls.scm.controls.repositoryUrl.value).toBe('');
    expect(imported.controls.scm.controls.credentialsId.value).toBe('');

    imported.controls.name.setValue('ledger');

    expect(imported.controls.nexusIqApplications.length).toBe(0);
    expect(imported.controls.scm.controls.repositoryUrl.value).toBe(
      'https://bitbucket.bbh.com/projects/PAY/repos/pay-ledger',
    );
    expect(imported.controls.scm.controls.credentialsId.value).toBe('');
  });

  it('offers the import in the empty list of services', async () => {
    await start();
    editor()['remove'](0);
    await fixture.whenStable();

    const empty = page().querySelector('.empty-state')!;
    expect(empty.querySelector('dso-config-import')).not.toBeNull();
    expect(buttonOf(empty as HTMLElement, 'Add service').disabled).toBe(false);
  });

  it('marks the stored services that load invalid and names them as the reason', async () => {
    const twoApplications = service({
      nexusIqApplications: [
        ...service().nexusIqApplications,
        { ...service().nexusIqApplications[0], application: 'cert-gui-legacy' },
      ],
    });
    await edit([twoApplications, anotherService()]);

    expect(text(panels()[0].querySelector('.tag.error'))).toBe('Needs attention');
    expect(panels()[1].querySelector('.tag.error')).toBeNull();
    expect(text(page().querySelector('.blocked'))).toBe(
      'Complete these services before adding another: gui (Nexus IQ).',
    );
  });

  it('names the Node.js fields of a stored service that gives the installation and its home', async () => {
    const both = service({
      nodeBuild: nodeBuild({
        enabled: true,
        installationName: 'NodeJS-20',
        nodePath: '/opt/node-20',
      }),
    });
    await edit([both, anotherService()]);

    expect(text(panels()[0].querySelector('.tag.error'))).toBe('Needs attention');
    expect(text(page().querySelector('.blocked'))).toBe(
      'Complete these services before adding another: gui (Node.js installation, Node.js home on the agent).',
    );
  });

  it('copies the values of the service chosen in the menu, taking those that identify it from its name', async () => {
    await edit([
      service(),
      anotherService({
        nodeBuild: nodeBuild({ enabled: true, directory: 'frontend' }),
        versionCatalogs: ['gradle/libs.versions.toml'],
      }),
    ]);

    buttonOf(page(), 'Copy values from existing service').click();
    await fixture.whenStable();
    expect([...document.querySelectorAll('.dso-menu button')].map(text)).toEqual(['gui', 'api']);

    buttonOf(document.querySelector('.dso-menu')!, 'api').click();
    await fixture.whenStable();
    const copy = editor()['form'].controls.services.at(2).getRawValue();

    expect(names()).toEqual(['gui', 'api', 'api-copy']);
    expect(panels()[2].querySelector('.tag.error')).toBeNull();
    expect(copy.id).toBeNull();
    expect(copy.build.tool).toBe(anotherService().build.tool);
    expect(copy.sonar.projectKey).toBe('');
    expect(copy.nexusIqApplications.map((app) => app.application)).toEqual(['cert-api-copy']);
    expect(copy.scm.repositoryUrl).toBe(anotherService().scm.repositoryUrl);
    expect(copy.nodeBuild).toMatchObject({ enabled: true, directory: 'frontend' });
    expect(copy.versionCatalogs).toBe('gradle/libs.versions.toml');
    expect(editor()['expanded']()).toBe(2);
  });

  it('keeps the empty Bitbucket credentials ID of a copied service, also when it is renamed', async () => {
    const gui = service({ scm: { ...service().scm, credentialsId: '' } });
    await edit([gui, anotherService()]);

    editor()['duplicate'](0);
    await fixture.whenStable();
    const copy = editor()['form'].controls.services.at(1);

    expect(copy.controls.name.value).toBe('gui-copy');
    expect(copy.controls.scm.controls.credentialsId.value).toBe('');

    copy.controls.name.setValue('ledger');

    expect(copy.controls.nexusIqApplications.at(0).controls.application.value).toBe('cert-ledger');
    expect(copy.controls.scm.controls.credentialsId.value).toBe('');
  });

  it('puts a copy in place of the blank new service and lists only the other services to copy', async () => {
    await edit();
    buttonOf(page(), 'Add service').click();
    await fixture.whenStable();

    buttonOf(page(), 'Copy values from existing service').click();
    await fixture.whenStable();
    expect([...document.querySelectorAll('.dso-menu button')].map(text)).toEqual(['gui', 'api']);

    buttonOf(document.querySelector('.dso-menu')!, 'gui').click();
    await fixture.whenStable();

    expect(names()).toEqual(['gui', 'api', 'gui-copy']);
    expect(editor()['expanded']()).toBe(2);
  });

  it('asks a new product for its Jenkins agents, starting with those of the service template', async () => {
    await start();

    expect(inputOf(page(), 'Jenkins agents').value).toBe('linux-agent');

    fillValidProduct();
    editor()['form'].controls.agentLabels.setValue('docker-agent, linux-agent');
    await submit();

    const request = http.expectOne({ method: 'POST', url: '/api/products' });
    expect(request.request.body.agentLabels).toEqual(['docker-agent', 'linux-agent']);
    request.flush(product({ id: 5 }));
    await fixture.whenStable();
  });

  it('explains the product fields and those of the open service with a help text', async () => {
    await start();

    expect(unexplained(page())).toEqual([]);
    expect(helpOf(fieldOf(page(), 'Owner team')!)).toBe(
      'The team that looks after the product. Shown in the product list, on the product page and in Pipeline Monitoring, where products can also be searched by it.',
    );
    expect(helpOf(fieldOf(page(), 'Service name')!)).toContain('Must be unique within the product');
  });

  it('leaves the Jenkins agents of a stored product to its pipelines', async () => {
    await edit();

    expect(fieldOf(page(), 'Jenkins agents')).toBeNull();

    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/products/1' });
    expect(request.request.body).not.toHaveProperty('agentLabels');
    request.flush(product());
    await fixture.whenStable();
  });

  it('adds a service imported from a config.yaml with the agents of its pipeline and lists the notes', async () => {
    await start();
    fillValidProduct();

    editor()['importService']({
      file: 'config.yaml',
      name: 'gateway',
      service: { ...anotherService(), id: 11 },
      notes: [
        { field: 'tools.sonar.foo', message: 'not imported: the portal has no such setting' },
      ],
      agentLabels: ['docker-agent'],
    });
    await fixture.whenStable();
    const added = editor()['form'].controls.services.at(1);

    expect(names()).toEqual(['gui', 'gateway']);
    expect(added.controls.id.value).toBeNull();
    expect(added.controls.build.controls.tool.value).toBe(anotherService().build.tool);
    expect(editor()['form'].controls.agentLabels.value).toBe('docker-agent');
    expect(text(page().querySelector('dso-import-notes li'))).toBe(
      'tools.sonar.foo: The portal has no such setting',
    );

    buttonOf(page().querySelector('dso-import-notes')!, 'Dismiss').click();
    await fixture.whenStable();

    expect(page().querySelector('dso-import-notes')).toBeNull();
  });
});
