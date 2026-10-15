import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  Department,
  MonitoringOverview as Overview,
  MonitoringStatus,
  PortfolioActivity,
} from '../core/models';
import { UNREACHABLE } from '@common/core/errors';
import { buttonOf, text } from '@common/testing/dom';
import { chartOptions } from '@common/testing/highcharts';
import {
  department,
  doraSummary,
  monitoringOverview,
  monitoringStatus,
  portfolioActivity,
  productHealth,
} from '../testing/fixtures';
import { MonitoringOverview } from './monitoring-overview';

describe('MonitoringOverview', () => {
  let fixture: ComponentFixture<MonitoringOverview>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MonitoringOverview],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MonitoringOverview);
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const cards = () => [...page().querySelectorAll<HTMLAnchorElement>('a.product')];
  const tiles = () => [...page().querySelectorAll('.stat')].map((tile) => tile.textContent?.trim());
  const headings = () => [...page().querySelectorAll('.department-title h3')].map(text);
  const fundServices = department({ id: 5, name: 'Fund Services', productCount: 1 });

  async function load(
    overview: Overview = monitoringOverview(),
    status: MonitoringStatus = monitoringStatus(),
    activity: PortfolioActivity = portfolioActivity(),
    departments: Department[] = [department(), fundServices],
  ) {
    fixture.detectChanges();
    http.expectOne('/api/monitoring/status').flush(status);
    http.expectOne('/api/monitoring/products').flush(overview);
    http.expectOne('/api/departments').flush(departments);
    http.expectOne('/api/monitoring/activity?range=30d').flush(activity);
    await fixture.whenStable();
  }

  async function filter(text: string) {
    const input = page().querySelector<HTMLInputElement>('input[aria-label="Filter products"]')!;
    input.value = text;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  it('totals the pipelines of every product and links each product', async () => {
    await load(
      monitoringOverview({
        products: [
          productHealth(),
          productHealth({
            productId: 2,
            code: 'PAY',
            name: 'PayHub',
            ownerTeam: null,
            departmentId: 5,
            serviceCount: 1,
            pipelineCount: 1,
            overall: 'DISABLED',
            statusCounts: { DISABLED: 1, UNSTABLE: 1 },
            lastRunAt: null,
          }),
        ],
      }),
    );

    expect(page().querySelector('h1')?.textContent).toBe('DevSecOps Pipeline Monitoring');
    expect(tiles()).toEqual([
      '5Pipelines',
      '2Passed',
      '2Failed or passed with warnings',
      '1Keys invalidated',
    ]);
    expect(cards().map((card) => card.getAttribute('href'))).toEqual([
      '/monitoring/products/1',
      '/monitoring/products/2',
    ]);
    expect(cards()[0].textContent).toContain('3 pipelines · 2 services');
    expect(cards()[1].textContent).toContain('1 pipeline · 1 service');
    expect(cards()[1].textContent).toContain('No owner team');
    expect(cards()[1].textContent).toContain('No runs yet');
    expect(cards()[1].classList).toContain('overall-disabled');
    expect(headings()).toEqual(['Corporate Technology', 'Fund Services']);
    const chart = page().querySelector('.by-department dso-chart');
    expect(chart?.getAttribute('aria-label')).toBe(
      'Corporate Technology: 1 failed, 2 passed; Fund Services: 1 passed with warnings, 1 key invalidated',
    );
    expect(chartOptions(chart).xAxis[1].categories).toEqual([
      '3 pipelines · 1 product',
      '2 pipelines · 1 product',
    ]);
  });

  it('charts the DORA metrics and daily runs of all pipelines over 30 days', async () => {
    await load();

    expect(text(page().querySelector('dso-dora-tiles h2'))).toBe('Delivery performance (DORA)');
    expect(text(page().querySelector('dso-dora-tiles .section-help'))).toBe(
      'Four industry measures of how often and how safely changes reach production. Each is rated Elite, High, Medium or Low; Elite is best.',
    );
    expect([...page().querySelectorAll('dso-dora-tiles .tile-title')].map(text)).toEqual([
      'Deployment frequency',
      'Lead time for changes',
      'Change failure rate',
      'Time to restore',
    ]);
    expect(text(page().querySelector('dso-dora-tiles .portfolio h3'))).toBe('Runs per day');
    expect(text(page().querySelector('.portfolio .card-header .muted'))).toBe(
      '40 runs of all pipelines in the last 30 days',
    );
    expect(page().querySelector('.portfolio dso-activity-chart dso-chart')?.classList).toContain(
      'drawn',
    );
  });

  it('leaves the portfolio charts out until a pipeline has run', async () => {
    await load(
      monitoringOverview({ products: [productHealth({ departmentId: null })] }),
      monitoringStatus({ influxConfigured: false }),
      portfolioActivity({ dora: doraSummary({ runs: 0 }) }),
      [],
    );

    expect(page().querySelector('dso-dora-tiles')).toBeNull();
    expect(page().querySelector('.portfolio')).toBeNull();
    expect(headings()).toEqual(['Not in a department']);
    expect(cards().length).toBe(1);
  });

  it('filters the products by name, code or owner team', async () => {
    await load();

    await filter(' technology ');
    expect(cards().length).toBe(1);

    await filter('payhub');
    expect(cards().length).toBe(0);
    expect(page().querySelector('.empty-state h3')?.textContent).toBe(
      'No product matches "payhub"',
    );
  });

  it('invites adding a product when there is none and reloads on refresh', async () => {
    await load(monitoringOverview({ products: [] }));

    expect(page().querySelector('.empty-state h3')?.textContent).toBe('No products yet');
    expect(page().querySelector('.empty-state a')?.getAttribute('href')).toBe(
      '/admin/products/new',
    );

    page().querySelector<HTMLButtonElement>('.actions button')!.click();
    fixture.detectChanges();
    http.expectOne('/api/monitoring/status').flush(monitoringStatus());
    http.expectOne('/api/monitoring/products').flush(monitoringOverview());
    http.expectOne('/api/departments').flush([department()]);
    http.expectOne('/api/monitoring/activity?range=30d').flush(portfolioActivity());
    await fixture.whenStable();

    expect(cards().length).toBe(1);
  });

  it('loads the departments again on refresh, so their error clears and new ones are known', async () => {
    fixture.detectChanges();
    http.expectOne('/api/monitoring/status').flush(monitoringStatus());
    http
      .expectOne('/api/monitoring/products')
      .flush(monitoringOverview({ products: [productHealth({ departmentId: 5 })] }));
    http.expectOne('/api/departments').error(new ProgressEvent('error'), { status: 0 });
    http.expectOne('/api/monitoring/activity?range=30d').flush(portfolioActivity());
    await fixture.whenStable();

    expect(text(page().querySelector('.products .banner span'))).toBe(
      `The products could not be loaded. ${UNREACHABLE}`,
    );

    buttonOf(page().querySelector('.actions')!, 'Refresh').click();
    fixture.detectChanges();
    http.expectOne('/api/monitoring/status').flush(monitoringStatus());
    http
      .expectOne('/api/monitoring/products')
      .flush(monitoringOverview({ products: [productHealth({ departmentId: 5 })] }));
    http.expectOne('/api/departments').flush([department(), fundServices]);
    http.expectOne('/api/monitoring/activity?range=30d').flush(portfolioActivity());
    await fixture.whenStable();

    expect(page().querySelector('.products .banner')).toBeNull();
    expect(headings()).toEqual(['Fund Services']);
  });

  it('says why the delivery performance is missing when it could not be read', async () => {
    await load(
      monitoringOverview(),
      monitoringStatus(),
      portfolioActivity({
        dora: doraSummary({ runs: 0 }),
        metricsError: 'InfluxDB could not be read: timeout',
      }),
    );

    const notice = () => page().querySelector('.dora-unavailable');
    expect(page().querySelector('dso-dora-tiles')).toBeNull();
    expect(text(notice()!.querySelector('h2'))).toBe('Delivery performance (DORA)');
    expect(text(notice()!.querySelector('.banner span'))).toBe(
      'Delivery performance and runs per day could not be loaded. The run results could not be read (InfluxDB could not be read: timeout). Try again in a moment; if it keeps failing, tell the portal administrator.',
    );

    buttonOf(notice()!, 'Try again').click();
    fixture.detectChanges();
    http
      .expectOne('/api/monitoring/activity?range=30d')
      .flush({ detail: 'The range must be 7d, 30d, 90d or 180d' }, { status: 400, statusText: '' });
    await fixture.whenStable();

    expect(text(notice()!.querySelector('.banner span'))).toBe(
      'Delivery performance and runs per day could not be loaded. The range must be 7d, 30d, 90d or 180d',
    );

    buttonOf(notice()!, 'Try again').click();
    fixture.detectChanges();
    http.expectOne('/api/monitoring/activity?range=30d').flush(portfolioActivity());
    await fixture.whenStable();

    expect(notice()).toBeNull();
    expect(page().querySelector('dso-dora-tiles')).not.toBeNull();
  });

  it('explains missing or unreachable metrics', async () => {
    await load(
      monitoringOverview({ metricsError: 'InfluxDB timed out' }),
      monitoringStatus({ influxReachable: false, influxError: 'connection refused' }),
    );

    const banners = [...page().querySelectorAll('.banner')].map(text);
    expect(banners).toEqual([
      'Run results could not be loaded: InfluxDB, where the pipelines report their runs, does not answer (connection refused). Try again in a moment; if it keeps failing, tell the portal administrator.',
      'Run results could not be loaded, so the statuses below may be incomplete (InfluxDB timed out). Try again in a moment; if it keeps failing, tell the portal administrator.',
    ]);
  });

  it('says what is not shown while InfluxDB is not configured and tells the administrator what to set', async () => {
    await load(
      monitoringOverview(),
      monitoringStatus({ influxConfigured: false }),
      portfolioActivity({
        dora: doraSummary({ runs: 0 }),
        metricsError: 'InfluxDB is not configured for the portal',
      }),
    );

    const banner = page().querySelector('dso-metrics-banner .banner.info')!;
    expect(text(banner)).toContain(
      "Run results are not shown: the portal is not connected to InfluxDB, where the pipelines report their runs, so it only knows whether each pipeline's key is valid.",
    );
    expect(text(banner.querySelector('.admin'))).toBe(
      'For the administrator: set INFLUX_URL and INFLUX_TOKEN.',
    );
    expect(page().querySelector('.dora-unavailable')).toBeNull();
  });

  it('shows why the overview could not be read', async () => {
    fixture.detectChanges();
    http.expectOne('/api/monitoring/status').flush(monitoringStatus());
    http
      .expectOne('/api/monitoring/products')
      .flush({ detail: 'Database unavailable' }, { status: 503, statusText: 'Unavailable' });
    http.expectOne('/api/departments').flush([department()]);
    http.expectOne('/api/monitoring/activity?range=30d').flush(portfolioActivity());
    await fixture.whenStable();

    expect(text(page().querySelector('.banner span'))).toBe(
      'The products could not be loaded. Database unavailable',
    );

    buttonOf(page(), 'Try again').click();
    fixture.detectChanges();
    http.expectOne('/api/monitoring/status').flush(monitoringStatus());
    http.expectOne('/api/monitoring/products').flush(monitoringOverview());
    http.expectOne('/api/monitoring/activity?range=30d').flush(portfolioActivity());
    http.expectOne('/api/departments').flush([department()]);
    await fixture.whenStable();

    expect(page().querySelector('.banner')).toBeNull();
    expect(cards().length).toBe(1);
  });
});
