import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { MY_DEPARTMENT_KEY } from '@common/departments/my-department';
import { PipelineHealth } from '../core/models';
import { PipelineDialog } from '../products/pipeline-dialog';
import {
  buttonOf,
  choose,
  gridCell,
  gridColumn,
  gridFilter,
  gridHeaders,
  gridRows,
  selectOf,
  sortBy,
  text,
} from '@common/testing/dom';
import { department, monitoringPipeline, pipelineHealth, pipelineRun } from '../testing/fixtures';
import { PipelineFilters, PipelineList, matches } from './pipeline-list';

const gui = pipelineHealth();
const api = pipelineHealth({
  pipeline: monitoringPipeline({
    id: 101,
    serviceId: 11,
    serviceName: 'api',
    type: 'SECURITY',
    jenkinsJob: 'DevSecOps/CERT/api-security',
    activeKey: null,
  }),
  status: 'DISABLED',
  lastRun: pipelineRun({ time: '2026-10-02T07:30:00Z' }),
});
const gateway = pipelineHealth({
  pipeline: monitoringPipeline({
    id: 102,
    productId: 2,
    productCode: 'PAY',
    productName: 'Payments Hub',
    serviceId: 20,
    serviceName: 'gateway',
    type: 'NEXUS_IQ',
    jenkinsJob: null,
  }),
  status: 'FAILURE',
  lastRun: null,
});

const noFilters: PipelineFilters = {
  service: '',
  product: '',
  type: 'ALL',
  job: '',
  key: 'ALL',
  status: 'ALL',
};

describe('the rows of the pipelines table', () => {
  const rows = [gui, api, gateway];
  const services = (list: PipelineHealth[]) => list.map((row) => row.pipeline.serviceName);

  it('filters by every column, the product by name or code', () => {
    const filtered = (filters: Partial<PipelineFilters>) =>
      services(rows.filter((row) => matches(row, { ...noFilters, ...filters })));

    expect(filtered({})).toEqual(['gui', 'api', 'gateway']);
    expect(filtered({ service: ' GA ' })).toEqual(['gateway']);
    expect(filtered({ product: 'pay' })).toEqual(['gateway']);
    expect(filtered({ product: 'cert' })).toEqual(['gui', 'api']);
    expect(filtered({ type: 'SECURITY' })).toEqual(['api']);
    expect(filtered({ job: 'security' })).toEqual(['api']);
    expect(filtered({ job: 'x' })).toEqual([]);
    expect(filtered({ key: 'ACTIVE' })).toEqual(['gui', 'gateway']);
    expect(filtered({ key: 'INVALIDATED' })).toEqual(['api']);
    expect(filtered({ status: 'FAILURE' })).toEqual(['gateway']);
  });
});

describe('PipelineList', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<PipelineList>;

  const page = () => fixture.nativeElement as HTMLElement;
  const shownServices = () => gridColumn(page(), 'service');
  const cells = (row: HTMLElement, ...columns: string[]) =>
    columns.map((column) => text(gridCell(row, column)));

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  async function open(departmentId: string | null) {
    if (departmentId === null) {
      localStorage.removeItem(MY_DEPARTMENT_KEY);
    } else {
      localStorage.setItem(MY_DEPARTMENT_KEY, departmentId);
    }
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PipelineList);
    await settle();
    http
      .expectOne('/api/departments')
      .flush([department(), department({ id: 5, name: 'Fund Services' })]);
    await settle();
  }

  async function listed(rows = [gui, api, gateway], metricsError: string | null = null) {
    await open('3');
    http.expectOne('/api/pipelines?departmentId=3').flush({ pipelines: rows, metricsError });
    await settle();
  }

  async function pick(select: HTMLSelectElement, option: string) {
    choose(select, option);
    await settle();
  }

  async function filter(label: string, value: string) {
    const input = gridFilter(page(), label);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await settle();
  }

  async function sort(column: string, clicks = 1) {
    for (let click = 0; click < clicks; click++) {
      await sortBy(page(), column);
    }
    return shownServices();
  }

  afterEach(() => {
    http.verify();
    localStorage.removeItem(MY_DEPARTMENT_KEY);
  });

  it('asks for the department first and remembers the one chosen', async () => {
    await open(null);

    expect(text(page().querySelector('h1'))).toBe('DevSecOps Pipelines');
    expect(text(page().querySelector('.empty-state h3'))).toBe(
      'Choose your department to see its pipelines',
    );
    expect(selectOf(page(), 'Your department').selectedOptions[0].textContent?.trim()).toBe(
      'Choose your department',
    );
    expect(text(page().querySelector('.department dso-hint'))).toBe(
      'Only the pipelines of this department are listed. This browser remembers your choice.',
    );
    expect(page().querySelector('dso-grid')).toBeNull();

    await pick(selectOf(page(), 'Your department'), 'Fund Services');
    expect(localStorage.getItem(MY_DEPARTMENT_KEY)).toBe('5');
    http.expectOne('/api/pipelines?departmentId=5').flush({ pipelines: [], metricsError: null });
    await settle();

    expect(text(page().querySelector('.empty-state h3'))).toBe('No pipelines in Fund Services yet');
    expect(text(page().querySelector('.empty-state a'))).toBe('Set up pipelines in Self-service');
    expect(page().querySelector('.empty-state a')?.getAttribute('href')).toBe('/self-service');
  });

  it('lists the pipelines of the remembered department with their key and last run', async () => {
    await listed();

    expect(selectOf(page(), 'Your department').selectedOptions[0].textContent?.trim()).toBe(
      'Corporate Technology',
    );
    expect(gridHeaders(page())).toEqual([
      'Service',
      'Product',
      'Type',
      'Jenkins job',
      'Pipeline key',
      'Last run',
      'Finished',
      '',
    ]);
    expect(text(page().querySelector('.list h2'))).toBe('Pipelines of Corporate Technology');
    expect(text(page().querySelector('.list .section-help'))).toContain(
      "Pipeline key: the secret code the service's Jenkins job uses to fetch its settings from this portal.",
    );
    const [first, second, third] = gridRows(page());
    expect(cells(first, 'service', 'product', 'type', 'job', 'key', 'status')).toEqual([
      'gui',
      'CertScanner CERT',
      'Full',
      'DevSecOps/CERT/gui-full',
      '6f1c2d3e…9abc',
      'Passed',
    ]);
    expect(text(gridCell(first, 'actions'))).toBe('Edit');
    expect(text(gridCell(second, 'key'))).toBe('Invalidated');
    expect(second.classList).toContain('muted');
    expect(first.classList).not.toContain('muted');
    expect(cells(third, 'type', 'job', 'lastRun')).toEqual(['Nexus IQ GoldenFix', 'Not set', '']);
    expect(text(page().querySelector('.shown'))).toBe('3 pipelines');
    expect(gridCell(first, 'service').querySelector('a')?.getAttribute('href')).toBe(
      '/pipelines/100',
    );
    expect(text(page().querySelector('.page-header a'))).toBe('Set up pipelines in Self-service');
    expect(page().querySelector('.page-header a')?.getAttribute('href')).toBe('/self-service');

    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    gridCell(third, 'type').click();
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/pipelines', 102]);
  });

  it('filters the table in its header', async () => {
    await listed();

    await filter('product', 'pay');
    expect(shownServices()).toEqual(['gateway']);
    expect(text(page().querySelector('.shown'))).toBe('1 of 3 pipelines shown');
    await filter('product', '');

    await pick(gridFilter(page(), 'pipeline key'), 'Invalidated');
    expect(shownServices()).toEqual(['api']);
    await pick(gridFilter(page(), 'pipeline key'), 'All');
    await pick(gridFilter(page(), 'type'), 'Nexus IQ GoldenFix');
    expect(shownServices()).toEqual(['gateway']);
    await pick(gridFilter(page(), 'type'), 'All');
    await pick(gridFilter(page(), 'last run'), 'Failed');
    expect(shownServices()).toEqual(['gateway']);
    await filter('service', 'gui');
    expect(gridRows(page())).toEqual([]);
    expect(text(page().querySelector('.dso-grid-empty'))).toBe('No pipeline matches the filters.');
    await filter('service', '');
    await pick(gridFilter(page(), 'last run'), 'All');
    await filter('Jenkins job', 'api-');
    expect(shownServices()).toEqual(['api']);
  });

  it('sorts in the header the type by its order, the key active first and the runs by time', async () => {
    await listed();

    expect(await sort('service')).toEqual(['api', 'gateway', 'gui']);
    expect(await sort('service')).toEqual(['gui', 'gateway', 'api']);
    expect(await sort('service')).toEqual(['gui', 'api', 'gateway']);
    expect(await sort('type', 2)).toEqual(['gateway', 'api', 'gui']);
    expect(await sort('key')).toEqual(['gui', 'gateway', 'api']);
    expect(await sort('lastRun', 2)).toEqual(['gui', 'api', 'gateway']);
    expect(await sort('status')).toEqual(['gui', 'gateway', 'api']);
    expect(await sort('job')).toEqual(['gateway', 'api', 'gui']);
  });

  it('saves the settings of a pipeline from its row and keeps its masked key', async () => {
    await listed();
    const saved = { ...gui.pipeline, agentLabels: ['docker'], activeKey: null };
    const opened = vi
      .spyOn(TestBed.inject(Dialog), 'open')
      .mockReturnValue({ closed: of(saved) } as unknown as DialogRef<unknown>);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');

    buttonOf(page(), 'Edit the settings of the Full pipeline of gui').click();
    await settle();

    expect(opened.mock.calls[0][0]).toBe(PipelineDialog);
    expect(opened.mock.calls[0][1]?.data).toMatchObject({
      service: { serviceId: 10, serviceName: 'gui' },
      pipeline: gui.pipeline,
    });
    expect(navigate).not.toHaveBeenCalled();
    expect(text(gridCell(gridRows(page())[0], 'key'))).toBe('6f1c2d3e…9abc');
  });

  it('says when the metrics or the pipelines could not be read', async () => {
    await listed([gui], 'InfluxDB timed out');

    expect(text(page().querySelector('dso-metrics-banner'))).toContain('InfluxDB timed out');

    await pick(selectOf(page(), 'Your department'), 'Fund Services');
    http
      .expectOne('/api/pipelines?departmentId=5')
      .flush({ detail: 'Department 5 does not exist' }, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(text(page().querySelector('.banner .banner-text'))).toBe(
      'The pipelines could not be loaded. Department 5 does not exist',
    );

    buttonOf(page(), 'Try again').click();
    await settle();
    http.expectOne('/api/pipelines?departmentId=5').flush({ pipelines: [gui], metricsError: null });
    await settle();

    expect(page().querySelector('.banner')).toBeNull();
    expect(shownServices()).toEqual(['gui']);
  });

  it('says when the departments could not be loaded and loads them again', async () => {
    localStorage.setItem(MY_DEPARTMENT_KEY, '3');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PipelineList);
    await settle();
    http
      .expectOne('/api/departments')
      .flush({ detail: 'The database is not available' }, { status: 503, statusText: 'Down' });
    http.expectOne('/api/pipelines?departmentId=3').flush({ pipelines: [], metricsError: null });
    await settle();

    expect(text(page().querySelector('.banner .banner-text'))).toBe(
      'The departments could not be loaded. The database is not available',
    );

    buttonOf(page(), 'Try again').click();
    await settle();
    http.expectOne('/api/departments').flush([department()]);
    await settle();

    expect(page().querySelector('.banner')).toBeNull();
    expect(text(page().querySelector('.empty-state h3'))).toBe(
      'No pipelines in Corporate Technology yet',
    );
  });
});
