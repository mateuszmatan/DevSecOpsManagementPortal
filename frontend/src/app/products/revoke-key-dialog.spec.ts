import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { text } from '@common/testing/dom';
import { pipeline } from '../testing/fixtures';
import { RevokeKeyDialog } from './revoke-key-dialog';

describe('RevokeKeyDialog', () => {
  let fixture: ComponentFixture<RevokeKeyDialog>;
  let http: HttpTestingController;
  const close = vi.fn();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [RevokeKeyDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DIALOG_DATA, useValue: pipeline() },
        { provide: DialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RevokeKeyDialog);
    await fixture.whenStable();
  });

  afterEach(() => {
    http.verify();
    close.mockReset();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const reason = () => page().querySelector<HTMLTextAreaElement>('textarea')!;

  async function submit(typed: string) {
    reason().value = typed;
    reason().dispatchEvent(new Event('input'));
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('names the pipeline and asks for a reason first', async () => {
    expect(text(page().querySelector('h2'))).toBe('Invalidate the key of the pipeline gui · Full?');
    expect(text(page().querySelector('.banner'))).toBe(
      'The full pipeline of gui is refused its settings from now on and stops at its next start. This key cannot be used again; to let the pipeline run later, regenerate its key.',
    );
    expect(page().querySelector('.banner strong')?.textContent).toBe('gui');

    await submit('');

    http.expectNone('/api/pipelines/100/keys/revoke');
    expect(page().querySelector('dso-error')?.textContent).toBe('Required');
  });

  it('invalidates the key with the trimmed reason and closes with the updated pipeline', async () => {
    await submit('  Leaked in a build log ');

    const request = http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys/revoke' });
    expect(request.request.body).toEqual({ reason: 'Leaked in a build log' });
    const updated = pipeline({ activeKey: null });
    request.flush(updated);

    expect(close).toHaveBeenCalledWith(updated);
  });

  it('shows why the key could not be invalidated', async () => {
    await submit('Service retired');

    http
      .expectOne('/api/pipelines/100/keys/revoke')
      .flush({ detail: 'The key is already invalidated' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(page().querySelector('[role=alert]')?.textContent).toBe(
      'The key is already invalidated',
    );
    expect(close).not.toHaveBeenCalled();
  });
});

describe('RevokeKeyDialog of a Nexus IQ GoldenFix pipeline', () => {
  it('names the pipeline by its label', async () => {
    TestBed.configureTestingModule({
      imports: [RevokeKeyDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DIALOG_DATA, useValue: pipeline({ type: 'NEXUS_IQ' }) },
        { provide: DialogRef, useValue: { close: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(RevokeKeyDialog);
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('.banner')?.textContent).toContain(
      'The Nexus IQ GoldenFix pipeline of',
    );
  });
});
