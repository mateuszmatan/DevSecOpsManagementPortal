import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ProductMonitoring } from '../core/models';
import {
  monitoringPipeline,
  pipelineHealth,
  pipelineRun,
  productMonitoring,
} from '../testing/fixtures';
import { ProductMonitoringPage } from './product-monitoring';
import { buttonOf, gridCell, gridRows, settleGrid, text } from '@common/testing/dom';

describe('ProductMonitoringPage', () => {
  let fixture: ComponentFixture<ProductMonitoringPage>;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductMonitoringPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(ProductMonitoringPage);
    fixture.componentRef.setInput('id', '1');
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const rows = () => gridRows(page());

  async function load(data: ProductMonitoring = productMonitoring()) {
    fixture.detectChanges();
    http.expectOne('/api/monitoring/products/1').flush(data);
    await fixture.whenStable();
  }

  it('lists the pipelines of the product with the latest run linked to its build', async () => {
    await load();

    expect(page().querySelector('h1')?.textContent).toBe('CertScanner');
    expect(page().querySelector('.summary')?.textContent).toContain('1 pipeline');
    const link = rows()[0].querySelector<HTMLAnchorElement>('a.build-link')!;
    expect(link.getAttribute('href')).toBe(
      'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/job/develop/42/',
    );
    expect(link.textContent?.trim()).toBe('#42');
    expect(gridCell(rows()[0], 'lastRun').textContent).toContain('develop');
    expect(text(rows()[0].querySelector('.stages'))).toBe(
      '10 of 12 passed 1 with warnings, 1 failed',
    );
    expect(rows()[0].querySelector('.stage-problems')?.classList).toContain('failing');
    expect(text(page().querySelector('.summary'))).toBe('1 pipeline: 1 passed');
    expect(text(gridCell(rows()[0], 'jenkins'))).toBe('Open in Jenkins');
  });

  it('opens a pipeline when its row is clicked', async () => {
    await load();

    gridCell(rows()[0], 'service').click();
    await settleGrid();

    expect(router.navigate).toHaveBeenCalledWith(['/monitoring/pipelines', 100]);
  });

  it('invites adding pipelines to a product without any', async () => {
    await load(productMonitoring({ pipelines: [], description: null }));

    expect(page().querySelector('.empty-state h3')?.textContent).toBe('No pipelines yet');
    expect(page().querySelector('.page-header p')?.textContent).toBe(
      'Status of the latest run of each pipeline.',
    );
  });

  it('shows why the product could not be read', async () => {
    fixture.detectChanges();
    http
      .expectOne('/api/monitoring/products/1')
      .flush({ detail: 'Product 1 was not found' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(text(page().querySelector('.banner span'))).toBe(
      'The product could not be loaded. Product 1 was not found',
    );

    buttonOf(page(), 'Try again').click();
    TestBed.tick();
    http.expectOne('/api/monitoring/products/1').flush(productMonitoring());
    await fixture.whenStable();

    expect(page().querySelector('.banner')).toBeNull();
    expect(page().querySelector('h1')?.textContent).toBe('CertScanner');
  });

  it('reads the product again on refresh and shows the progress meanwhile', async () => {
    await load();
    const refresh = buttonOf(page(), 'Refresh');

    refresh.click();
    TestBed.tick();

    expect(page().querySelector('dso-loading')).not.toBeNull();
    expect(refresh.disabled).toBe(true);
    http.expectOne('/api/monitoring/products/1').flush(productMonitoring());
    await fixture.whenStable();

    expect(page().querySelector('dso-loading')).toBeNull();
    expect(page().querySelector('h1')?.textContent).toBe('CertScanner');
  });
});
