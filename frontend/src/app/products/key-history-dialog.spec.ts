import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { Pipeline, PipelineKey } from '../core/models';
import { pipeline, revokedKey } from '../testing/fixtures';
import { KeyHistoryDialog } from './key-history-dialog';
import { buttonOf, gridColumn, gridHeaders, text } from '@common/testing/dom';

describe('KeyHistoryDialog', () => {
  let fixture: ComponentFixture<KeyHistoryDialog>;
  let http: HttpTestingController;
  let issued: Pipeline[];

  const active = pipeline().activeKey!;
  const revoked = revokedKey();
  const fresh: PipelineKey = {
    ...active,
    id: 1001,
    value: 'd'.repeat(36),
    hint: 'dddddddd…dddd',
    issuedAt: '2026-10-05T09:00:00Z',
  };
  const invalidated = () => pipeline({ activeKey: null, keys: [revoked] });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [KeyHistoryDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DIALOG_DATA, useValue: pipeline() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(KeyHistoryDialog);
    issued = [];
    fixture.componentInstance.keyIssued.subscribe((updated) => issued.push(updated));
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const cells = (column: string) => gridColumn(page(), column);

  async function open(loaded: Pipeline) {
    fixture.detectChanges();
    http.expectOne('/api/pipelines/100').flush(loaded);
    await fixture.whenStable();
  }

  it('lists every key by its hint and never shows a key value', async () => {
    await open(pipeline({ keys: [active, revoked] }));

    expect(text(page().querySelector('h2'))).toBe('Key history of the pipeline gui · Full');
    expect(text(page().querySelector('.intro'))).toBe(
      'Every key the pipeline of gui in CertScanner has had, by its first and last characters. Only the active key works; an invalidated key cannot be used again.',
    );
    expect(gridHeaders(page())).toEqual([
      'Key',
      'Status',
      'Issued',
      'Last used by Jenkins',
      'Invalidated',
    ]);
    expect(cells('key')).toEqual(['6f1c2d3e…9abc', '1a2b3c4d…eeff']);
    expect(page().textContent).not.toContain(active.value!);
    expect(cells('status')).toEqual(['Active', 'Invalidated']);
    expect(cells('lastUsedAt')[0]).toBe('Never');
    expect(cells('revoked')[0]).toBe('–');
    expect(cells('revoked')[1]).toContain('Leaked in a build log');
    expect(buttonOf(page(), 'Regenerate key')).toBeUndefined();
  });

  it('shows why the history could not be read', async () => {
    fixture.detectChanges();
    http
      .expectOne('/api/pipelines/100')
      .flush({ detail: 'Pipeline 100 was not found' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(page().querySelector('.banner')?.textContent).toBe('Pipeline 100 was not found');
    expect(page().querySelector('dso-grid')).toBeNull();
  });

  it('offers to regenerate the key of a pipeline whose key was invalidated', async () => {
    await open(invalidated());

    expect(page().querySelector('.key-status')?.textContent).toContain('Key invalidated');
    expect(buttonOf(page(), 'Regenerate key')).toBeDefined();
    expect(page().querySelector('svg-icon')).toBeNull();
  });

  it('regenerates the key, shows the new one and keeps the old key in the history', async () => {
    await open(invalidated());

    buttonOf(page(), 'Regenerate key')!.click();
    await fixture.whenStable();
    expect(buttonOf(page(), 'Regenerating…')?.disabled).toBe(true);
    const updated = pipeline({ activeKey: fresh, keys: [fresh, revoked] });
    http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys' }).flush(updated);
    await fixture.whenStable();

    expect(page().querySelector('.key-status .key-value')?.textContent).toBe('d'.repeat(36));
    expect(text(page().querySelector('.key-status'))).toContain(
      "Put it in the service's Jenkinsfile.",
    );
    expect(cells('key')).toEqual(['dddddddd…dddd', '1a2b3c4d…eeff']);
    expect(cells('status')).toEqual(['Active', 'Invalidated']);
    expect(buttonOf(page(), 'Regenerate key')).toBeUndefined();
    expect(issued).toEqual([updated]);
  });

  it('shows why the key could not be regenerated and lets it be tried again', async () => {
    await open(invalidated());

    buttonOf(page(), 'Regenerate key')!.click();
    http
      .expectOne('/api/pipelines/100/keys')
      .flush(
        { title: 'Not Found', detail: 'Pipeline 100 was not found' },
        { status: 404, statusText: 'Not Found' },
      );
    await fixture.whenStable();

    expect(page().querySelector('.banner')?.textContent).toBe('Pipeline 100 was not found');
    expect(buttonOf(page(), 'Regenerate key')?.disabled).toBe(false);
    expect(issued).toEqual([]);
  });
});

describe('KeyHistoryDialog of a Nexus IQ GoldenFix pipeline', () => {
  it('names the pipeline by its label', async () => {
    const nexusIq = pipeline({ type: 'NEXUS_IQ' });
    TestBed.configureTestingModule({
      imports: [KeyHistoryDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DIALOG_DATA, useValue: nexusIq },
      ],
    });
    const fixture = TestBed.createComponent(KeyHistoryDialog);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/pipelines/100')
      .flush({ ...nexusIq, keys: [] });
    await fixture.whenStable();

    expect(text((fixture.nativeElement as HTMLElement).querySelector('h2'))).toBe(
      'Key history of the pipeline gui · Nexus IQ GoldenFix',
    );
  });
});
