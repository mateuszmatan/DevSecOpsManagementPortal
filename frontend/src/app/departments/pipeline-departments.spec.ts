import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { gridCell, gridHeaders, gridRows, text } from '@common/testing/dom';
import { chartOptions } from '@common/testing/highcharts';
import { Department } from '../core/models';
import { department } from '../testing/fixtures';
import { PipelineDepartments } from './pipeline-departments';

describe('PipelineDepartments', () => {
  let fixture: ComponentFixture<PipelineDepartments>;
  let http: HttpTestingController;

  const fundServices = department({
    id: 5,
    name: 'Fund Services',
    productCount: 0,
    serviceCount: 0,
    pipelineCount: 0,
    activePipelineCount: 0,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PipelineDepartments],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PipelineDepartments);
  });

  async function load(departments: Department[] = [department(), fundServices]) {
    fixture.detectChanges();
    http.expectOne('/api/departments').flush(departments);
    await fixture.whenStable();
  }

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const row = (name: string) =>
    gridRows(page()).find((tr) => text(tr.querySelector('.name')) === name)!;

  it('lists the departments with their services and pipelines', async () => {
    await load();

    expect(text(page().querySelector('.summary'))).toBe(
      '2 departments with 1 product and 2 services',
    );
    expect(text(page().querySelector('.departments .section-help'))).toBe(
      'Every product belongs to one department, and people choose their department to see its products and pipelines. Only an empty department can be deleted.',
    );
    expect(gridHeaders(page())).toEqual(['Department', 'Products', 'Services', 'Pipelines', '']);
    expect(text(gridCell(row('Corporate Technology'), 'services'))).toBe('2');
    expect(text(gridCell(row('Corporate Technology'), 'pipelines'))).toBe('3, 1 key invalidated');
    expect(text(gridCell(row('Fund Services'), 'pipelines'))).toBe('None yet');
  });

  it('charts the active pipelines and the invalidated keys of every department', async () => {
    await load();
    const chart = page().querySelector('section.chart')!;

    expect(text(chart.querySelector('h2'))).toBe('Pipelines per department');
    expect([...chart.querySelectorAll('.legend span')].map(text)).toEqual([
      'Active',
      'Key invalidated',
    ]);
    expect(chart.querySelector('dso-chart')?.getAttribute('aria-label')).toBe(
      'Corporate Technology: 2 active, 1 key invalidated; Fund Services: none',
    );
    expect(chartOptions(chart.querySelector('dso-chart')).xAxis[1].categories).toEqual([
      '3 pipelines, 1 key invalidated',
      '0 pipelines',
    ]);
  });

  it('marks a department whose pipelines are all active', async () => {
    await load([department({ pipelineCount: 2, activePipelineCount: 2 })]);

    expect(text(gridCell(row('Corporate Technology'), 'pipelines'))).toBe('2, all active');
    expect(page().querySelectorAll('section.chart').length).toBe(1);
  });

  it('leaves the chart out while there are no departments', async () => {
    await load([]);

    expect(page().querySelector('section.chart')).toBeNull();
  });
});
