import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { PipelineMonitoring } from '../core/models';
import { buttonOf, gridCell, gridRows } from '@common/testing/dom';
import {
  doraSummary,
  monitoringPipeline,
  pipelineMonitoring,
  pipelineRun,
} from '../testing/fixtures';
import { PipelineMonitoringPage } from './pipeline-monitoring';

describe('PipelineMonitoringPage', () => {
  let fixture: ComponentFixture<PipelineMonitoringPage>;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PipelineMonitoringPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(PipelineMonitoringPage);
    fixture.componentRef.setInput('id', '100');
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const text = (selector: string) => page().querySelector(selector)?.textContent?.trim();

  async function load(data: PipelineMonitoring = pipelineMonitoring(), range = '30d') {
    fixture.detectChanges();
    http.expectOne(`/api/monitoring/pipelines/100?range=${range}`).flush(data);
    await fixture.whenStable();
  }

  it('shows the pipeline with its DORA tiles, latest run and Grafana dashboard', async () => {
    await load();

    expect(text('h1')).toContain('gui');
    expect(text('h1')).toContain('Full pipeline');
    expect([...page().querySelectorAll('.tile-title')].map((t) => t.textContent)).toEqual([
      'Deployment frequency',
      'Lead time for changes',
      'Change failure rate',
      'Time to restore',
    ]);
    expect(text('.last-run')).toContain('10 passed, 1 with warnings, 1 failed');
    expect(text('.last-run')).not.toContain('blocked');
    expect(text('.page-header .tags')?.replace(/\s+/g, ' ')).toBe(
      "Monitoring tags (the names this pipeline's results are stored under): project=CERT-gui, env=test",
    );
    expect(text('a.jenkins')).toBe('Open in Jenkins');
    expect(text('.page-header .actions a')).toBe('Manage pipeline');
    expect(text('.toolbar .zone')).toMatch(/^Times are in your time zone, .+\.$/);
    expect(text('.grafana h2')).toBe('Grafana');
    expect(page().querySelector('.grafana iframe')?.getAttribute('src')).toBe(
      'https://grafana.bbh.com/d/adzfc54123/pipeline?var-project=CERT-gui&kiosk',
    );
    expect(page().querySelector('.grafana a')?.getAttribute('href')).toBe(
      'https://grafana.bbh.com/d/adzfc54123/pipeline?var-project=CERT-gui',
    );
    expect(text('.grafana a')).toBe('Open in Grafana');
    expect(page().querySelector('a.jenkins')?.getAttribute('href')).toBe(
      'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/',
    );
    expect(text('.page')).not.toContain('The key of this pipeline is invalidated');
  });

  it('links every run to the build address the API sent for it', async () => {
    await load();

    expect(page().querySelector('.last-run a.build-link')?.getAttribute('href')).toBe(
      'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/job/develop/42/',
    );
    const builds = gridRows(page()).map((row) => gridCell(row, 'build'));
    expect(builds[0].querySelector('a')?.getAttribute('href')).toBe(
      'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/job/develop/42/',
    );
    expect(builds[1].querySelector('a')).toBeNull();
    expect(builds[1].textContent?.trim()).toBe('#41');
  });

  it('warns that a pipeline without an active key stops at start-up', async () => {
    await load(pipelineMonitoring({ pipeline: monitoringPipeline({ activeKey: null }) }));

    expect(text('.page')).toContain('The key of this pipeline is invalidated');
  });

  it('embeds the dashboard of every Grafana instance, each named and linked', async () => {
    await load(
      pipelineMonitoring({
        grafana: [
          {
            name: 'Grafana test',
            dashboardUrl: 'https://grafana.bbh.com/d/adzfc54123/p?var-project=CERT-gui',
          },
          {
            name: 'Grafana prod',
            dashboardUrl: 'https://grafana-prod.bbh.com/d/ad2trcm/s?var-project=CERT-gui',
          },
        ],
      }),
    );

    const cards = Array.from(page().querySelectorAll('.grafana'));
    expect(cards.map((card) => card.querySelector('h2')?.textContent?.trim())).toEqual([
      'Grafana test',
      'Grafana prod',
    ]);
    expect(cards.map((card) => card.querySelector('a')?.textContent?.trim())).toEqual([
      'Open in Grafana test',
      'Open in Grafana prod',
    ]);
    expect(cards.map((card) => card.querySelector('iframe')?.getAttribute('src'))).toEqual([
      'https://grafana.bbh.com/d/adzfc54123/p?var-project=CERT-gui&kiosk',
      'https://grafana-prod.bbh.com/d/ad2trcm/s?var-project=CERT-gui&kiosk',
    ]);
  });

  it('explains what is missing without runs, without Grafana and without metrics', async () => {
    await load(
      pipelineMonitoring({
        lastRun: null,
        recentRuns: [],
        grafana: [],
        metricsError: 'InfluxDB timed out',
        pipeline: monitoringPipeline({ jenkinsJobUrl: null }),
      }),
    );

    expect(text('.last-run')).toContain('No run reported yet');
    expect(text('.page')).toContain('No runs in this period.');
    expect(text('.grafana h2')).toBe('Grafana dashboard');
    expect(text('.grafana .missing')?.replace(/\s+/g, ' ')).toBe(
      'No Grafana dashboard is linked to the portal, so the detailed charts of this pipeline are not shown here. For the administrator: set GRAFANA_DASHBOARD_URL, and GRAFANA_2_DASHBOARD_URL for a second Grafana.',
    );
    expect(text('.page')).toContain('InfluxDB timed out');
    expect(page().querySelector('a.jenkins')).toBeNull();
  });

  it('keeps an unknown range at 30 days and switches the range in the address', async () => {
    fixture.componentRef.setInput('range', '5y');
    await load();

    expect(text('.toolbar .period-label')).toBe('Period');
    expect(page().querySelector('dso-toggle-group')?.getAttribute('aria-labelledby')).toBe(
      'period-label',
    );
    expect(
      [...page().querySelectorAll('dso-toggle-group button')].map((button) =>
        button.textContent?.trim(),
      ),
    ).toEqual(['7 days', '30 days', '90 days', '180 days']);
    expect(buttonOf(page(), '30 days').getAttribute('aria-checked')).toBe('true');
    buttonOf(page(), '90 days').click();

    expect(router.navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { range: '90d' }, replaceUrl: true }),
    );
  });

  it('shows why the pipeline could not be read', async () => {
    fixture.detectChanges();
    http
      .expectOne('/api/monitoring/pipelines/100?range=30d')
      .flush({ detail: 'Pipeline 100 was not found' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(text('.banner span')).toBe(
      'The pipeline could not be loaded. Pipeline 100 was not found',
    );
    expect(text('a.btn')).toBe('Back to monitoring');
    expect(buttonOf(page(), 'Try again')).toBeTruthy();
  });

  it('reads the metrics again on refresh and shows the progress meanwhile', async () => {
    await load();
    const refresh = page().querySelector<HTMLButtonElement>(
      'button[aria-label="Refresh the pipeline metrics"]',
    )!;
    expect(page().querySelector('dso-loading')).toBeNull();

    refresh.click();
    TestBed.tick();

    expect(page().querySelector('dso-loading')).not.toBeNull();
    expect(refresh.disabled).toBe(true);
    http
      .expectOne('/api/monitoring/pipelines/100?range=30d')
      .flush(pipelineMonitoring({ dora: doraSummary({ deployments: 13 }) }));
    await fixture.whenStable();

    expect(page().querySelector('dso-loading')).toBeNull();
    expect(refresh.disabled).toBe(false);
  });

  it('warns on the time to restore tile since when the pipeline is failing', async () => {
    await load(pipelineMonitoring({ dora: doraSummary({ failingSince: '2026-10-04T08:00:00Z' }) }));

    const alerts = page().querySelectorAll('.tile-alert');
    expect(alerts.length).toBe(1);
    expect(alerts[0].textContent).toMatch(
      /^Not recovered yet: a deployment failed on 4 Oct, \d\d:00 \S+ and its pipeline has not deployed successfully since$/,
    );
  });
});
