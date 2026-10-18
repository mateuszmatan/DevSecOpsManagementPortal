import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ActionHistoryApi } from '../core/api';
import { ActionLog } from '../core/models';
import { ActionHistoryPage } from './action-history';

const SAMPLE_LOGS: ActionLog[] = [
  {
    id: 1,
    createdAt: '2026-10-16T08:00:00Z',
    username: 'mateusz.matan',
    userDisplayName: 'Mateusz Matan',
    userEmail: 'mateusz.matan@bbh.com',
    departmentName: 'Corporate Technology',
    actionType: 'CREATE_PRODUCT',
    resourceType: 'PRODUCT',
    resourceName: 'CertScanner',
    details: 'Created product CERT with 2 service(s)',
    status: 'SUCCESS',
  },
  {
    id: 2,
    createdAt: '2026-10-16T08:30:00Z',
    username: 'mateusz.matan',
    userDisplayName: 'Mateusz Matan',
    userEmail: 'mateusz.matan@bbh.com',
    departmentName: 'Corporate Technology',
    actionType: 'REVOKE_KEY',
    resourceType: 'PIPELINE_KEY',
    resourceName: 'backend-api (FULL)',
    details: 'Invalidated active key. Reason: Security breach',
    status: 'SUCCESS',
  },
];

describe('ActionHistoryPage', () => {
  let fixture: ComponentFixture<ActionHistoryPage>;
  let component: ActionHistoryPage;
  let api: { list: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    api = { list: vi.fn().mockReturnValue(of(SAMPLE_LOGS)) };

    await TestBed.configureTestingModule({
      imports: [ActionHistoryPage],
      providers: [provideRouter([]), { provide: ActionHistoryApi, useValue: api }],
    }).compileComponents();

    fixture = TestBed.createComponent(ActionHistoryPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders the page and list of logs', () => {
    expect(component).toBeTruthy();
    expect(component['columns'].length).toBe(7);
  });

  it('filters rows by query', () => {
    component['filters'].controls.search.setValue('CertScanner');
    fixture.detectChanges();
    expect(component['filteredRows']().length).toBe(1);
    expect(component['filteredRows']()[0].resourceName).toBe('CertScanner');
  });

  it('shows actions, resources and statuses in words rather than codes', async () => {
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const cells = (column: string) =>
      [...page.querySelectorAll(`[col-id="${column}"].ag-cell`)].map((cell) => cell.textContent!.trim());

    expect(cells('action')).toEqual(['Create product', 'Revoke key']);
    expect(cells('resource')).toEqual(['Product CertScanner', 'Pipeline key backend-api (FULL)']);
    expect(cells('status')).toEqual(['Success', 'Success']);
  });

  it('finds a row by the words of its action', () => {
    component['filters'].controls.search.setValue('revoke');
    fixture.detectChanges();
    expect(component['filteredRows']().map((row) => row.id)).toEqual([2]);
  });

  it('filters rows by action type', () => {
    component['filters'].controls.actionType.setValue('REVOKE_KEY');
    fixture.detectChanges();
    expect(component['filteredRows']().length).toBe(1);
    expect(component['filteredRows']()[0].actionType).toBe('REVOKE_KEY');
  });
});
