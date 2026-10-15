import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Pipeline, Product } from '../core/models';
import { CodeDialog } from '../shared/code-dialog';
import { ConfirmDialog } from '@common/shared/confirm-dialog';
import {
  anotherService,
  department,
  globalSettings,
  pipeline,
  product,
  revokedKey,
  service,
  servicePipelines,
} from '../testing/fixtures';
import { GeneratedKeys } from './generated-keys';
import { KeyHistoryDialog } from './key-history-dialog';
import { PipelineDialog } from './pipeline-dialog';
import { ProductDetail } from './product-detail';
import { RevokeKeyDialog } from './revoke-key-dialog';
import { buttonOf, text, toast } from '@common/testing/dom';

describe('ProductDetail', () => {
  let fixture: ComponentFixture<ProductDetail>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductDetail],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductDetail);
    fixture.componentRef.setInput('id', '1');
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;

  async function load(stored: Product = product(), services = [servicePipelines()]) {
    fixture.detectChanges();
    http.expectOne('/api/products/1').flush(stored);
    http.expectOne('/api/products/1/pipelines').flush(services);
    http
      .expectOne('/api/departments')
      .flush([department({ id: 5, name: 'Fund Services' }), department()]);
    await fixture.whenStable();
  }

  const invalidated = () =>
    servicePipelines({ pipelines: [pipeline({ activeKey: null, keys: [revokedKey()] })] });
  const regenerated = () =>
    pipeline({
      activeKey: {
        ...pipeline().activeKey!,
        id: 1001,
        value: 'c'.repeat(36),
        hint: 'cccccccc…cccc',
      },
    });
  const fact = (label: string) =>
    text(
      [...page().querySelectorAll('.facts > div')]
        .find((entry) => text(entry.querySelector('dt')) === label)
        ?.querySelector('dd'),
    );
  const snackText = () => text(toast());

  function withScm(scm: Partial<Product['services'][number]['scm']>): Product {
    const stored = service();
    return product({ services: [{ ...stored, scm: { ...stored.scm, ...scm } }] });
  }

  it('shows the product, its counts and its pipelines without icons', async () => {
    await load();

    expect(page().querySelector('h1')?.textContent).toBe('CertScanner');
    expect(
      [...page().querySelectorAll('.breadcrumb > :not(.sep)')].map((part) => part.textContent),
    ).toEqual(['DevSecOps Admin', 'Products', 'Corporate Technology', 'CertScanner']);
    expect(fact('Department')).toBe('Corporate Technology');
    expect(fact('Services')).toBe('1');
    expect(fact('Pipelines')).toBe('1, active');
    expect(page().querySelector('.stats')).toBeNull();
    expect(page().querySelector('.pipeline-title strong')?.textContent).toBe('Full pipeline');
    expect(page().querySelector('svg-icon')).toBeNull();
  });

  it('leaves the department out of a product that is not in one', async () => {
    await load(product({ departmentId: null }));

    expect(page().querySelector('.breadcrumb')?.textContent).not.toContain('Corporate');
    expect(page().querySelector('.facts')?.textContent).not.toContain('Department');
  });

  it('shows the AppScan key ID only of a product that has one', async () => {
    await load();
    expect(text(page().querySelector('.technical'))).toBe(
      'Security scans sign in to HCL AppScan with the API key bbh_key',
    );

    fixture.componentInstance['product'].set(product({ appScan: null, services: [] }));
    fixture.detectChanges();
    expect(page().querySelector('.page-header')?.textContent).not.toContain('AppScan');
  });

  it('links the repository URL of the service', async () => {
    await load();

    expect(page().querySelector('.repository a')?.getAttribute('href')).toBe(
      'https://bitbucket.bbh.com/projects/CERT/repos/gui',
    );
  });

  it('builds the repository link from the Bitbucket fields when the service has no URL', async () => {
    await load(
      withScm({
        repositoryUrl: null,
        apiUrl: 'https://bitbucket.bbh.com',
        projectKey: 'TA',
        repoSlug: 'cert-scanner',
      }),
    );

    expect(page().querySelector('.repository a')?.textContent).toBe(
      'https://bitbucket.bbh.com/projects/TA/repos/cert-scanner',
    );
  });

  it('shows the last REST fetch of a key only when one is recorded', async () => {
    const fetched = pipeline({
      id: 101,
      type: 'SAST',
      activeKey: { ...pipeline().activeKey!, lastUsedAt: '2026-10-04T07:00:00Z' },
    });
    await load(product(), [servicePipelines({ pipelines: [pipeline(), fetched] })]);

    const dates = [...page().querySelectorAll('.key-dates')].map((dates) => dates.textContent);
    expect(dates[0]).not.toContain('last used');
    expect(dates[0]).not.toContain('not used');
    expect(dates[1]).toContain('last used');
  });

  it('reveals and hides the active key with a text button', async () => {
    await load();
    const toggle = () => page().querySelector<HTMLButtonElement>('.key .text-link')!;

    expect(page().querySelector('.key-value')?.textContent?.trim()).toBe('6f1c2d3e…9abc');
    expect(toggle().textContent?.trim()).toBe('Show key');
    expect(toggle().getAttribute('aria-label')).toBe('Show the key of the full pipeline');

    toggle().click();
    await fixture.whenStable();

    expect(page().querySelector('.key-value')?.textContent?.trim()).toBe(
      pipeline().activeKey!.value,
    );
    expect(toggle().textContent?.trim()).toBe('Hide key');
  });

  it('says why the product could not be read and leads back to the list', async () => {
    fixture.detectChanges();
    http
      .expectOne('/api/products/1')
      .flush(
        { title: 'Not Found', detail: 'Product 1 does not exist' },
        { status: 404, statusText: 'Not Found' },
      );
    http.expectOne('/api/products/1/pipelines').flush([]);
    http.expectOne('/api/departments').flush(null, { status: 500, statusText: 'Error' });
    await fixture.whenStable();

    expect(text(page().querySelector('.banner'))).toBe(
      'The product could not be loaded. Product 1 does not exist',
    );
    expect(page().querySelector<HTMLAnchorElement>('a[href="/admin/products"].btn')).not.toBeNull();
    expect(buttonOf(page(), 'Try again')).toBeDefined();
    expect(page().querySelector('.breadcrumb')?.textContent).toContain('Product');
    expect(page().querySelector('h1')).toBeNull();
  });

  it('shows the jobs a pipeline starts and links its own job only when Jenkins is known', async () => {
    await load(product(), [
      servicePipelines({
        pipelines: [
          pipeline({
            type: 'SECURITY',
            jenkinsJobUrl: null,
            extendedPipelineJob: 'DevSecOps/CERT/gui-extended',
            description: 'Nightly security build',
          }),
          pipeline({
            id: 101,
            type: 'EXTENDED',
            jenkinsJob: null,
            jenkinsJobUrl: null,
            securityPipelineJob: 'DevSecOps/CERT/gui-security',
          }),
        ],
      }),
    ]);
    const meta = (index: number) =>
      Object.fromEntries(
        [
          ...page().querySelectorAll('.pipeline')[index].querySelectorAll('.pipeline-meta > div'),
        ].map((entry) => [
          entry.querySelector('dt')?.textContent,
          entry.querySelector('dd')?.textContent?.trim(),
        ]),
      );

    expect(meta(0)).toMatchObject({
      'Jenkins job': 'DevSecOps/CERT/gui-full',
      Starts: 'DevSecOps/CERT/gui-extended',
      'Monitoring tags': 'CERT-gui · test',
    });
    expect(text(page().querySelector('.pipeline-description'))).toBe('Nightly security build');
    expect(page().querySelectorAll('.pipeline')[0].querySelector('.pipeline-meta a')).toBeNull();
    expect(meta(1)).toMatchObject({
      'Jenkins job': 'Not set',
      'Started by': 'DevSecOps/CERT/gui-security',
    });
    expect(
      [...page().querySelectorAll('.pipeline-actions')].map((actions) => text(actions)),
    ).toEqual(['No Jenkins link More', 'No Jenkins link More']);
  });

  describe('new services', () => {
    const twoServices = () => [
      servicePipelines(),
      servicePipelines({
        serviceId: 11,
        serviceName: 'api',
        pipelines: [pipeline({ id: 110, serviceId: 11, serviceName: 'api' })],
      }),
    ];

    it('announces the key generated for the new service and shows it', async () => {
      TestBed.inject(GeneratedKeys).record(1, ['gui']);
      await load();

      const notice = page().querySelector('.generated');
      expect(notice?.getAttribute('role')).toBe('status');
      expect(notice?.textContent).toContain('Pipeline key generated for the new service gui.');
      expect(page().querySelector('.service-title .tag.new')?.textContent).toBe('New');
      expect(page().querySelector('.key-value')?.textContent?.trim()).toBe(
        pipeline().activeKey!.value,
      );
    });
  });

  describe('invalidated keys', () => {
    it('regenerates the key without asking and shows the new one', async () => {
      await load(product(), [invalidated()]);
      const open = vi.spyOn(TestBed.inject(Dialog), 'open');

      buttonOf(page(), 'Regenerate key')!.click();
      await fixture.whenStable();
      expect(buttonOf(page(), 'Regenerating…')?.disabled).toBe(true);
      const request = http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys' });
      expect(request.request.body).toEqual({});
      request.flush(regenerated());
      await fixture.whenStable();

      expect(open).not.toHaveBeenCalled();
      expect(page().querySelector('.key-value')?.textContent?.trim()).toBe('c'.repeat(36));
      expect(page().querySelector('.key-state')?.textContent).toBe('Key active');
      expect(buttonOf(page(), 'Regenerate key')).toBeUndefined();
      expect(fact('Pipelines')).toBe('1, active');
      expect(snackText()).toContain(
        'Full pipeline of gui has a new key: pass it in the Jenkinsfile',
      );
    });
  });

  describe('actions', () => {
    const spyOnOpen = () => vi.spyOn(TestBed.inject(Dialog), 'open');
    let open: ReturnType<typeof spyOnOpen>;
    const closed = (result: unknown) => ({ closed: of(result) }) as unknown as DialogRef<unknown>;

    beforeEach(() => {
      open = spyOnOpen().mockReturnValue(closed(undefined));
      vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    });

    const closingWith = (...results: unknown[]) =>
      results.forEach((result) => open.mockReturnValueOnce(closed(result)));
    const opened = (index = 0) => ({
      component: open.mock.calls[index][0],
      data: open.mock.calls[index][1]?.data as Record<string, unknown>,
    });
    const snack = snackText;

    async function menu(label: string, trigger = 'More actions of the full pipeline') {
      buttonOf(page(), trigger).click();
      await fixture.whenStable();
      buttonOf(document, label).click();
      await fixture.whenStable();
    }

    it('opens the configuration of the product and of a pipeline', async () => {
      await load();

      await menu('Settings sent to Jenkins (config.yaml)', 'More actions of CertScanner');
      http.expectOne('/api/products/1/config').flush('projects: {}');
      await menu('Settings sent to Jenkins (config.yaml)');
      http.expectOne('/api/pipelines/100/config').flush('projects:\n  gui: {}');

      expect(opened(0)).toMatchObject({
        component: CodeDialog,
        data: {
          title: 'Settings sent to Jenkins (config.yaml) for CertScanner',
          code: 'projects: {}',
          fileName: 'cert-config.yaml',
        },
      });
      expect(opened(1).data['fileName']).toBe('cert-gui-full.yaml');
    });

    it('names a Nexus IQ GoldenFix pipeline in its labels, configuration and delete question', async () => {
      const nexusIq = pipeline({
        type: 'NEXUS_IQ',
        entryPoint: 'devSecOpsNexusIqGoldenFixPipeline',
      });
      await load(product(), [
        servicePipelines({ pipelines: [pipeline({ id: 101, type: 'SAST' }), nexusIq] }),
      ]);
      const labels = (selector: string) =>
        [...page().querySelectorAll(selector)].map((element) => element.getAttribute('aria-label'));

      expect(
        [...page().querySelectorAll('.pipeline-title strong')].map((title) => title.textContent),
      ).toEqual(['SAST scanning pipeline', 'Nexus IQ GoldenFix pipeline']);
      expect(labels('.key .text-link')).toEqual([
        'Show the key of the SAST scanning pipeline',
        'Copy the key of the SAST scanning pipeline',
        'Show the key of the Nexus IQ GoldenFix pipeline',
        'Copy the key of the Nexus IQ GoldenFix pipeline',
      ]);

      await menu('Delete pipeline', 'More actions of the Nexus IQ GoldenFix pipeline');
      await menu(
        'Settings sent to Jenkins (config.yaml)',
        'More actions of the Nexus IQ GoldenFix pipeline',
      );
      http.expectOne('/api/pipelines/100/config').flush('projects: {}');

      expect(opened(0).data['message']).toContain(
        'The Nexus IQ GoldenFix pipeline of gui and its key history are deleted.',
      );
      expect(opened(1).data).toMatchObject({
        title: 'Settings sent to Jenkins for gui · Nexus IQ GoldenFix (config.yaml)',
        fileName: 'cert-gui-nexusiq.yaml',
      });
    });

    it('offers one Jenkinsfile for the services that share a pipeline type, this one first', async () => {
      const api = pipeline({
        id: 200,
        serviceId: 11,
        serviceName: 'api',
        activeKey: { ...pipeline().activeKey!, value: 'a1b2c3d4-0000-4abc-9def-123456789abc' },
      });
      await load(product(), [
        servicePipelines(),
        servicePipelines({ serviceId: 11, serviceName: 'api', pipelines: [api] }),
      ]);

      await menu('Jenkinsfile for several services');
      http.expectOne('/api/settings').flush(globalSettings());

      expect(opened().data['title']).toBe('Jenkinsfile for several services');
      expect(opened().data['subtitle']).toContain('One run builds gui, api');
      expect(opened().data['code']).toContain(
        "pipelineKeys: [\n    '6f1c2d3e-0000-4abc-9def-123456789abc',\n    'a1b2c3d4",
      );
    });

    it('joins only the extended pipelines that read the same security pipeline', async () => {
      const extended = (id: number, serviceId: number, serviceName: string, job: string) =>
        pipeline({
          id,
          serviceId,
          serviceName,
          type: 'EXTENDED',
          securityPipelineJob: job,
          activeKey: { ...pipeline().activeKey!, value: `${id}-0000-4abc-9def-123456789abc` },
        });
      await load(product(), [
        servicePipelines({ pipelines: [extended(100, 10, 'gui', 'CERT/security')] }),
        servicePipelines({
          serviceId: 11,
          serviceName: 'api',
          pipelines: [extended(200, 11, 'api', 'CERT/security')],
        }),
        servicePipelines({
          serviceId: 12,
          serviceName: 'batch',
          pipelines: [extended(300, 12, 'batch', 'CERT/batch-security')],
        }),
      ]);

      await menu('Jenkinsfile for several services', 'More actions of the extended pipeline');
      http.expectOne('/api/settings').flush(globalSettings());

      expect(opened().data['subtitle']).toContain('One run builds gui, api;');
    });

    it('replaces the key once confirmed and reveals the new one', async () => {
      await load();
      closingWith(true);

      await menu('Replace key');
      expect(opened()).toMatchObject({
        component: ConfirmDialog,
        data: { title: 'Replace the key of the pipeline gui · Full?', danger: true },
      });
      const replaced = pipeline();
      http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys' }).flush({
        ...replaced,
        activeKey: {
          ...replaced.activeKey!,
          id: 501,
          value: 'b'.repeat(64),
          hint: 'bbbbbbbb…bbbb',
        },
      });
      await fixture.whenStable();

      expect(page().querySelector('.key-value')?.textContent?.trim()).toBe('b'.repeat(64));
      expect(snack()).toContain('New key issued');
    });

    it('invalidates the key with the reason the dialog asks for', async () => {
      await load();
      closingWith(pipeline({ activeKey: null }));

      await menu('Invalidate key');
      await fixture.whenStable();

      expect(opened().component).toBe(RevokeKeyDialog);
      expect(page().querySelector('.revoked-note')).not.toBeNull();
      expect(fact('Pipelines')).toBe('1, 1 key invalidated');
      expect(snack()).toContain('Key invalidated');
    });

    it('adds a pipeline in type order and saves the settings of another', async () => {
      await load();
      const sast = pipeline({ id: 101, type: 'SAST' });
      closingWith(sast, { ...sast, description: 'Nightly scan' });

      buttonOf(page(), 'Add pipeline').click();
      await fixture.whenStable();
      expect(opened()).toMatchObject({ component: PipelineDialog });
      expect(
        [...page().querySelectorAll('.pipeline-title strong')].map((title) => title.textContent),
      ).toEqual(['Full pipeline', 'SAST scanning pipeline']);
      expect(snack()).toContain('SAST scanning pipeline added to gui');

      await menu('Edit pipeline settings');
      expect((opened(1).data['pipeline'] as Pipeline).id).toBe(100);
      expect(snack()).toContain('Pipeline settings saved');
    });

    it('deletes the product once confirmed and returns to the list', async () => {
      await load();
      closingWith(true, true);

      await menu('Delete product', 'More actions of CertScanner');
      expect(opened().data).toMatchObject({
        title: 'Delete the product CertScanner?',
        confirmLabel: 'Delete product',
        danger: true,
      });
      http.expectOne({ method: 'DELETE', url: '/api/products/1' }).flush(null);
      await fixture.whenStable();
      expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/admin/products']);
      expect(snack()).toContain('CertScanner was deleted with its services and pipelines.');

      await menu('Delete pipeline');
      http
        .expectOne('/api/pipelines/100')
        .flush({ detail: 'Pipeline 100 is in use' }, { status: 409, statusText: 'Conflict' });
      await fixture.whenStable();
      expect(snack()).toContain('Pipeline 100 is in use');
    });

    it('copies the key and says so', async () => {
      await load();

      buttonOf(page(), 'Copy the key of the full pipeline').click();
      await fixture.whenStable();

      expect(snack()).toContain('Key copied to the clipboard');
    });
  });
});
