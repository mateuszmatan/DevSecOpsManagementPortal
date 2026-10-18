import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { text } from '@common/testing/dom';
import { ProductRequest } from '../core/models';
import { ServiceFields } from '../products/service-fields';
import { anotherService, pipeline, product, service } from '../testing/fixtures';
import { ServiceConfigDialog, ServiceConfigDialogData } from './service-config-dialog';

describe('ServiceConfigDialog', () => {
  let fixture: ComponentFixture<ServiceConfigDialog>;
  let http: HttpTestingController;
  const close = vi.fn();

  async function render(
    stored = product({ services: [service(), anotherService()] }),
    data: ServiceConfigDialogData = { pipeline: pipeline({ serviceId: 11, serviceName: 'api' }) },
  ) {
    TestBed.configureTestingModule({
      imports: [ServiceConfigDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DIALOG_DATA, useValue: data },
        { provide: DialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ServiceConfigDialog);
    fixture.detectChanges();
    http.expectOne('/api/products/1').flush(stored);
    await fixture.whenStable();
  }

  afterEach(() => {
    http.verify();
    close.mockReset();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const save = async () => {
    page().querySelector<HTMLButtonElement>('.modal-footer .btn-primary')!.click();
    await fixture.whenStable();
  };

  const sectionsOf = () =>
    fixture.debugElement.query(By.directive(ServiceFields)).componentInstance.pipeline();

  it('names the product and the pipeline whose sections it shows', async () => {
    await render();

    expect(text(page().querySelector('h2'))).toBe('Configure api');
    expect(text(page().querySelector('.modal-body .subtitle'))).toBe(
      'CertScanner (CERT) Full pipeline',
    );
    expect(sectionsOf()).toBe('FULL');
  });

  it('shows every section when it configures the whole service', async () => {
    await render(undefined, {
      pipeline: pipeline({ serviceId: 11, serviceName: 'api', type: 'SECURITY' }),
      allSections: true,
    });

    expect(text(page().querySelector('.modal-body .subtitle'))).toBe('CertScanner (CERT)');
    expect(sectionsOf()).toBeNull();
  });

  it('saves the changed service of the pipeline at the version read, without starting pipelines', async () => {
    await render();
    fixture.componentInstance['serviceForm']()!.controls.description.setValue('REST API');

    await save();
    const request = http.expectOne({ method: 'PUT', url: '/api/products/1' });
    const body = request.request.body as ProductRequest;
    request.flush(product());
    await fixture.whenStable();

    expect(request.request.params.keys()).toEqual([]);
    expect(body.version).toBe(3);
    expect(body.services.map((s) => [s.id, s.description])).toEqual([
      [10, 'Angular front end'],
      [11, 'REST API'],
    ]);
    expect(close).toHaveBeenCalledWith(true);
  });

  it('keeps the dialog open with the reason when the save is refused', async () => {
    await render();

    await save();
    http
      .expectOne({ method: 'PUT', url: '/api/products/1' })
      .flush({ detail: 'The record was changed by someone else' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(text(page().querySelector('.banner'))).toContain('The record was changed by someone else');
    expect(close).not.toHaveBeenCalled();
  });

  it('says why the service settings could not be loaded', async () => {
    TestBed.configureTestingModule({
      imports: [ServiceConfigDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DIALOG_DATA, useValue: { pipeline: pipeline() } },
        { provide: DialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ServiceConfigDialog);
    fixture.detectChanges();
    http
      .expectOne('/api/products/1')
      .flush({ detail: 'Database unavailable' }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();

    expect(text(page().querySelector('.banner .banner-text'))).toBe(
      'The service settings could not be loaded. Database unavailable',
    );
  });

  it('edits no other service when the service of the pipeline is gone', async () => {
    await render(product({ services: [service()] }));

    expect(text(page().querySelector('.banner'))).toBe(
      'The service of this pipeline is no longer part of CertScanner.',
    );
    expect(page().querySelector('dso-service-fields')).toBeNull();
  });
});
