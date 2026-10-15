import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EventEmitter } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Pipeline, PipelineMonitoring } from '../core/models';
import { KeyHistoryDialog } from '../products/key-history-dialog';
import { PipelineDialog } from '../products/pipeline-dialog';
import { RevokeKeyDialog } from '../products/revoke-key-dialog';
import { CodeDialog } from '../shared/code-dialog';
import { ConfirmDialog } from '@common/shared/confirm-dialog';
import { buttonOf, gridColumn, gridRows, text, toast } from '@common/testing/dom';
import {
  globalSettings,
  pipeline,
  pipelineMonitoring,
  pipelineRun,
  revokedKey,
} from '../testing/fixtures';
import { PipelinePage } from './pipeline-page';

const spyOnOpen = () => vi.spyOn(TestBed.inject(Dialog), 'open');

describe('PipelinePage', () => {
  let fixture: ComponentFixture<PipelinePage>;
  let http: HttpTestingController;
  let router: Router;
  let open: ReturnType<typeof spyOnOpen>;

  const closed = (result: unknown) => ({ closed: of(result) }) as unknown as DialogRef<unknown>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PipelinePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    open = spyOnOpen().mockReturnValue(closed(undefined));
    fixture = TestBed.createComponent(PipelinePage);
    fixture.componentRef.setInput('id', '100');
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const opened = (index = 0) => ({
    component: open.mock.calls[index][0],
    data: open.mock.calls[index][1]?.data as Record<string, unknown>,
  });

  async function load(
    stored: Pipeline = pipeline({ keys: [pipeline().activeKey!] }),
    monitoring: PipelineMonitoring = pipelineMonitoring(),
  ) {
    fixture.detectChanges();
    http.expectOne('/api/settings').flush(globalSettings());
    http.expectOne('/api/pipelines/100').flush(stored);
    http.expectOne('/api/monitoring/pipelines/100?range=30d').flush(monitoring);
    await fixture.whenStable();
  }

  async function openMore() {
    buttonOf(page(), 'More').click();
    await fixture.whenStable();
  }

  const settle = () => new Promise((resolve) => setTimeout(resolve));

  async function menu(label: string) {
    await openMore();
    buttonOf(document, label).click();
    await settle();
  }

  async function reloadedMonitoring(monitoring: PipelineMonitoring = pipelineMonitoring()) {
    await settle();
    http.expectOne('/api/monitoring/pipelines/100?range=30d').flush(monitoring);
    await fixture.whenStable();
  }

  const chip = () => text(page().querySelector('.title dso-status-chip'));

  const fact = (label: string) =>
    [...page().querySelectorAll('.rows > div')]
      .find((row) => text(row.querySelector('dt')) === label)!
      .querySelector('dd');

  it('shows the pipeline with its key, settings, Jenkinsfile and recent runs', async () => {
    await load();

    expect(text(page().querySelector('.breadcrumb'))).toBe(
      'DevSecOps Pipelines/CertScanner/gui · Full',
    );
    expect(text(page().querySelector('h1'))).toBe('gui · Full pipeline');
    expect(text(page().querySelector('.last-run .muted'))).toBe('Last run');
    expect(text(page().querySelector('.last-run dso-status-chip'))).toBe('Passed');
    expect(text(page().querySelector('.page-description'))).toBe(
      'The automated build, test and security checks Jenkins runs for gui, a service of CertScanner CERT.',
    );
    expect(text(page().querySelector('.key-value'))).toBe('6f1c2d3e…9abc');
    expect([...page().querySelectorAll('.rows dt')].map(text)).toEqual([
      'Issued',
      'Last used by Jenkins',
      'Earlier keys',
      'What it does',
      'Jenkins job',
      'Jenkins agents',
      'Monitoring tags',
      'Last changed',
    ]);
    expect(text(fact('Last used by Jenkins'))).toBe('Not yet');
    expect(text(fact('Earlier keys'))).toBe('None');
    expect(text(fact('Jenkins agents')?.querySelector('.mono'))).toBe('linux-agent');
    expect(text(fact('Jenkins agents')?.querySelector('.explain'))).toBe(
      'The machines the pipeline runs on',
    );
    expect(text(page().querySelector('.jenkinsfile .section-help'))).toBe(
      "The file to put in the service's repository so Jenkins runs this pipeline. It holds only the pipeline key: Jenkins fetches every other setting from this portal.",
    );
    expect(text(page().querySelector('pre.code-block'))).toBe(
      "@Library('DevSecOpsJenkinsLibrary') _ devSecOpsPipeline(pipelineKey: '6f1c2d3e-0000-4abc-9def-123456789abc')",
    );
    expect(gridColumn(page(), 'result')).toEqual(['Passed', 'Failed']);
    expect(gridColumn(page(), 'build')).toEqual(['#42', '#41']);
    expect(
      [...page().querySelectorAll<HTMLAnchorElement>('.page-header .actions a')].map((link) => [
        text(link),
        link.getAttribute('href'),
      ]),
    ).toEqual([
      ['Open in Jenkins', 'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/'],
    ]);
    expect(
      [...page().querySelectorAll('.page-header .actions button')].map((button) => [
        text(button),
        button.classList.contains('btn-primary'),
      ]),
    ).toEqual([
      ['More', false],
      ['Edit settings', true],
    ]);
    expect(text(page().querySelector('.runs .section-help'))).toMatch(
      /^Up to 5 of the latest runs Jenkins reported in the past 30 days\. Times are in your time zone, .+\.$/,
    );
    const runs = page().querySelector<HTMLAnchorElement>('.runs .card-header a')!;
    expect(text(runs)).toBe('See every run and the delivery performance (DORA)');
    expect(runs.getAttribute('href')).toBe('/monitoring/pipelines/100');
    expect(page().querySelector('.banner.danger')).toBeNull();

    await openMore();
    const product = document.querySelector<HTMLAnchorElement>('.dso-menu a.dropdown-item')!;
    expect(text(product)).toBe('Open CertScanner in Admin');
    expect(product.getAttribute('href')).toBe('/admin/products/1');
    expect([...document.querySelectorAll('.dso-menu button')].map(text)).toEqual([
      'Settings sent to Jenkins (config.yaml)',
      'Key history',
      'Replace key',
      'Invalidate key',
      'Delete pipeline',
    ]);
  });

  it('reveals and hides the key', async () => {
    await load();

    buttonOf(page(), 'Show the key').click();
    await fixture.whenStable();
    expect(text(page().querySelector('.key-value'))).toBe('6f1c2d3e-0000-4abc-9def-123456789abc');

    buttonOf(page(), 'Hide the key').click();
    await fixture.whenStable();
    expect(text(page().querySelector('.key-value'))).toBe('6f1c2d3e…9abc');
  });

  it('shows at most five runs and says when none was reported', async () => {
    const runs = Array.from({ length: 7 }, (_, index) => pipelineRun({ build: 40 + index }));
    await load(undefined, pipelineMonitoring({ recentRuns: runs }));
    expect(gridRows(page()).length).toBe(5);

    fixture.componentRef.setInput('id', '101');
    fixture.detectChanges();
    http.expectOne('/api/pipelines/101').flush(pipeline({ id: 101 }));
    http
      .expectOne('/api/monitoring/pipelines/101?range=30d')
      .flush(pipelineMonitoring({ recentRuns: [], metricsError: 'InfluxDB timed out' }));
    await fixture.whenStable();

    expect(text(page().querySelector('.small-empty h3'))).toBe('No runs in the past 30 days');
    expect(text(page().querySelector('.small-empty p'))).toBe(
      'Runs show here once Jenkins runs this pipeline with its Jenkinsfile.',
    );
    expect(text(page().querySelector('dso-metrics-banner'))).toContain('InfluxDB timed out');
  });

  it('shows the runs as loading, says why they could not be loaded and tries again', async () => {
    fixture.detectChanges();
    http.expectOne('/api/settings').flush(globalSettings());
    http.expectOne('/api/pipelines/100').flush(pipeline());
    await settle();
    fixture.detectChanges();

    expect(page().querySelector('.runs dso-loading')).not.toBeNull();
    expect(page().querySelector('.runs .small-empty')).toBeNull();

    http
      .expectOne('/api/monitoring/pipelines/100?range=30d')
      .flush(null, { status: 502, statusText: 'Bad Gateway' });
    await fixture.whenStable();

    expect(text(page().querySelector('.runs .banner .banner-text'))).toBe(
      'The latest runs could not be loaded. The portal could not complete the request (error 502 Bad Gateway). Try again in a moment; if it keeps failing, tell the portal administrator.',
    );
    expect(page().querySelector('.runs .small-empty')).toBeNull();
    expect(page().querySelector('.runs dso-loading')).toBeNull();

    buttonOf(page().querySelector('.runs')!, 'Try again').click();
    await reloadedMonitoring();

    expect(page().querySelector('.runs .banner')).toBeNull();
    expect(gridColumn(page(), 'build')).toEqual(['#42', '#41']);
  });

  it('offers to regenerate an invalidated key and reveals the new one', async () => {
    await load(pipeline({ activeKey: null }), pipelineMonitoring({ status: 'DISABLED' }));

    expect(text(page().querySelector('.banner.danger .banner-text'))).toBe(
      "The pipeline key is invalidated. Jenkins is refused the settings of this pipeline, so the pipeline stops at its next start. Regenerate the key, then put the new Jenkinsfile in the service's repository.",
    );
    expect(text(page().querySelector('.title .last-run'))).toBe('Key invalidated');
    expect(page().querySelector('.last-run .muted')).toBeNull();
    expect(text(page().querySelector('.no-key'))).toBe(
      'The pipeline has no working key. Key history shows when and why it was invalidated.',
    );
    expect(buttonOf(page(), 'Edit settings').classList).toContain('btn-outline-primary');
    expect(page().querySelectorAll('.btn-primary').length).toBe(1);
    expect(text(page().querySelector('pre.code-block'))).toContain('<issue a new key first>');

    buttonOf(page(), 'Regenerate key').click();
    await fixture.whenStable();
    http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys' }).flush(pipeline());
    await reloadedMonitoring();

    expect(page().querySelector('.banner.danger')).toBeNull();
    expect(text(page().querySelector('.key-value'))).toBe('6f1c2d3e-0000-4abc-9def-123456789abc');
    expect(chip()).toBe('Passed');
  });

  it('counts the earlier keys and opens their history, which can issue a new key', async () => {
    const issued = new EventEmitter<Pipeline>();
    await load(pipeline({ keys: [pipeline().activeKey!, revokedKey()] }));
    open.mockReturnValueOnce({ componentInstance: { keyIssued: issued } } as DialogRef<unknown>);

    expect(text(fact('Earlier keys'))).toBe('1 key, all invalidated · Key history');

    buttonOf(fact('Earlier keys')!, 'Key history').click();
    await fixture.whenStable();
    expect(opened()).toMatchObject({ component: KeyHistoryDialog, data: { id: 100 } });

    const replaced = pipeline();
    issued.emit({ ...replaced, activeKey: { ...replaced.activeKey!, value: 'c'.repeat(36) } });
    await reloadedMonitoring();
    expect(text(page().querySelector('.key-value'))).toBe('c'.repeat(36));
  });

  it('copies the key and the Jenkinsfile and says which', async () => {
    await load();

    buttonOf(page(), 'Copy the key').click();
    await fixture.whenStable();
    expect(text(toast())).toContain('Key copied to the clipboard');

    buttonOf(page(), 'Copy the Jenkinsfile').click();
    await fixture.whenStable();
    expect(text(toast())).toContain('Jenkinsfile copied to the clipboard');
  });

  it('saves the settings in the pipeline dialog', async () => {
    await load();
    open.mockReturnValueOnce(closed(pipeline({ agentLabels: ['docker'] })));

    buttonOf(page(), 'Edit settings').click();
    await reloadedMonitoring(pipelineMonitoring({ recentRuns: [] }));

    expect(opened().component).toBe(PipelineDialog);
    expect(text(fact('Jenkins agents')?.querySelector('.mono'))).toBe('docker');
    expect(text(page().querySelector('.small-empty h3'))).toBe('No runs in the past 30 days');
  });

  it('shows the configuration and replaces the key once confirmed', async () => {
    await load();

    await menu('Settings sent to Jenkins (config.yaml)');
    http.expectOne('/api/pipelines/100/config').flush('projects: {}');
    expect(opened()).toMatchObject({
      component: CodeDialog,
      data: { code: 'projects: {}', fileName: 'cert-gui-full.yaml' },
    });

    open.mockReturnValueOnce(closed(true));
    await menu('Replace key');
    expect(opened(1).component).toBe(ConfirmDialog);
    const replaced = pipeline();
    http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys' }).flush({
      ...replaced,
      activeKey: { ...replaced.activeKey!, value: 'b'.repeat(64), hint: 'bbbbbbbb…bbbb' },
    });
    await reloadedMonitoring();

    expect(text(page().querySelector('.key-value'))).toBe('b'.repeat(64));
  });

  it('invalidates the key with the reason the dialog asks for', async () => {
    await load();
    open.mockReturnValueOnce(closed(pipeline({ activeKey: null })));

    expect(chip()).toBe('Passed');

    await menu('Invalidate key');
    await reloadedMonitoring(pipelineMonitoring({ status: 'DISABLED' }));

    expect(opened().component).toBe(RevokeKeyDialog);
    expect(page().querySelector('.banner.danger')).not.toBeNull();
    expect(chip()).toBe('Key invalidated');
  });

  it('deletes the pipeline once confirmed and returns to the pipelines', async () => {
    await load();
    open.mockReturnValueOnce(closed(true));

    await menu('Delete pipeline');
    http.expectOne({ method: 'DELETE', url: '/api/pipelines/100' }).flush(null);
    await fixture.whenStable();

    expect(router.navigate).toHaveBeenCalledWith(['/pipelines']);
  });

  it('says why the pipeline could not be read and leads back to the list', async () => {
    fixture.detectChanges();
    http.expectOne('/api/settings').flush(null, { status: 500, statusText: 'Error' });
    http
      .expectOne('/api/pipelines/100')
      .flush({ detail: 'Pipeline 100 does not exist' }, { status: 404, statusText: 'Not Found' });
    http
      .expectOne('/api/monitoring/pipelines/100?range=30d')
      .flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(text(page().querySelector('.banner'))).toBe(
      'The pipeline could not be loaded. Pipeline 100 does not exist',
    );
    expect(page().querySelector('a.btn')?.getAttribute('href')).toBe('/pipelines');
    expect(text(page().querySelector('.breadcrumb'))).toBe('DevSecOps Pipelines/Pipeline');
  });
});
