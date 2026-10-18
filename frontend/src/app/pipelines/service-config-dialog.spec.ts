import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { text } from '@common/testing/dom';
import { ProductRequest } from '../core/models';
import { anotherService, pipeline, product, service } from '../testing/fixtures';
import { ServiceConfigDialog } from './service-config-dialog';

describe('ServiceConfigDialog', () => {
  let fixture: ComponentFixture<ServiceConfigDialog>;
  let http: HttpTestingController;
  const close = vi.fn();

  async function render(stored = product({ services: [service(), anotherService()] })) {
    TestBed.configureTestingModule({
      imports: [ServiceConfigDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DIALOG_DATA, useValue: { pipeline: pipeline({ serviceId: 11, serviceName: 'api' }) } },
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

  it('edits no other service when the service of the pipeline is gone', async () => {
    await render(product({ services: [service()] }));

    expect(text(page().querySelector('.banner'))).toBe(
      'The service of this pipeline is no longer part of CertScanner.',
    );
    expect(page().querySelector('dso-service-fields')).toBeNull();
  });
});
