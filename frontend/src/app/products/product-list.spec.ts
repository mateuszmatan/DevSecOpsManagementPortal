import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Department, ProductSummary } from '../core/models';
import { gridCell, gridHeaders, gridRows, settleGrid, text } from '@common/testing/dom';
import { department, productSummary } from '../testing/fixtures';
import { ProductList } from './product-list';

describe('ProductList', () => {
  let fixture: ComponentFixture<ProductList>;
  let http: HttpTestingController;

  const summary = productSummary();
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
      imports: [ProductList],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductList);
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const cards = () => [...page().querySelectorAll<HTMLElement>('section.department')];
  const card = (name: string) =>
    cards().find((section) => text(section.querySelector('h2')) === name)!;
  async function load(
    products: ProductSummary[] = [summary],
    departments: Department[] = [department(), fundServices],
  ) {
    fixture.detectChanges();
    http.expectOne('/api/products').flush(products);
    http.expectOne('/api/departments').flush(departments);
    await fixture.whenStable();
  }

  it('lists the products of every department with the tally of its pipelines', async () => {
    await load();
    const row = gridRows(card('Corporate Technology'))[0];

    expect(cards().map((section) => text(section.querySelector('h2')))).toEqual([
      'Corporate Technology',
      'Fund Services',
    ]);
    expect(text(card('Corporate Technology').querySelector('.tally'))).toBe(
      '1 product, 3 pipelines, 1 key invalidated',
    );
    expect(card('Fund Services').querySelector('.tally')).toBeNull();
    expect(text(card('Fund Services').querySelector('.no-products'))).toBe(
      'No products in Fund Services yet.Add a product to Fund Services',
    );
    expect(text(page().querySelector('.summary'))).toBe('1 product in 2 departments');
    expect(gridHeaders(card('Corporate Technology'))).toEqual([
      'Product',
      'Owner team',
      'Services',
      'Pipelines',
    ]);
    expect(row.classList).toContain('clickable');
    expect(row.querySelector('.name')?.textContent).toBe('CertScanner');
    expect(row.querySelector('.code')?.textContent).toBe('CERT');
    expect(text(gridCell(row, 'services'))).toBe('2');
    expect(row.querySelector('.revoked')?.textContent?.trim()).toBe('3, 1 key invalidated');
    expect(page().querySelector('.hint')).toBeNull();
  });

  it('adds a product to an empty department from its card, and has one Add product button', async () => {
    await load();
    const addProduct = card('Fund Services').querySelector<HTMLAnchorElement>('a')!;

    expect(text(addProduct)).toBe('Add a product to Fund Services');
    expect(addProduct.getAttribute('href')).toBe('/admin/products/new?department=5');
    expect(card('Corporate Technology').querySelector('header a')).toBeNull();
    expect(
      [...page().querySelectorAll('a.btn')].map((link) => [text(link), link.getAttribute('href')]),
    ).toEqual([['Add product', '/admin/products/new']]);
    expect(page().querySelector('section.chart')).toBeNull();
    expect(page().querySelector('h1')).toBeNull();
  });

  it('gathers the products without a department in a last card summed from their rows', async () => {
    await load([
      summary,
      productSummary({ id: 7, name: 'Ledger', departmentId: null, departmentName: null }),
      productSummary({
        id: 8,
        name: 'Archive',
        departmentId: null,
        departmentName: null,
        pipelineCount: 1,
        activePipelineCount: 1,
      }),
    ]);
    const unassigned = cards().at(-1)!;

    expect(text(unassigned.querySelector('h2'))).toBe('Not in a department');
    expect(text(unassigned.querySelector('.tally'))).toBe(
      '2 products, 4 pipelines, 1 key invalidated',
    );
    expect(text(unassigned.querySelector('.hint'))).toBe(
      'Edit these products to choose their department.',
    );
    expect(gridRows(unassigned).length).toBe(2);
    expect(text(page().querySelector('.summary'))).toBe(
      '1 product in 2 departments, 2 not in a department',
    );
  });

  it('shows no partial tally for the products without a department while searching', async () => {
    await load();
    const input = page().querySelector<HTMLInputElement>('input[aria-label="Search products"]')!;
    input.value = 'ledger';
    input.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 300));
    fixture.detectChanges();
    http
      .expectOne('/api/products?search=ledger')
      .flush([productSummary({ id: 7, name: 'Ledger', departmentId: null, departmentName: null })]);
    await fixture.whenStable();

    expect(cards().map((section) => text(section.querySelector('h2')))).toEqual([
      'Not in a department',
    ]);
    expect(cards()[0].querySelector('.tally')).toBeNull();
    expect(text(page().querySelector('.summary'))).toBe(
      '0 products in 0 departments, 1 not in a department',
    );
  });

  it('invites to add the first product above the departments waiting for one', async () => {
    await load([]);

    expect(text(page().querySelector('.empty-state h3'))).toBe('No products yet');
    expect(page().querySelector('.empty-state a')?.getAttribute('href')).toBe(
      '/admin/products/new',
    );
    expect(cards().map((section) => text(section.querySelector('h2')))).toEqual([
      'Corporate Technology',
      'Fund Services',
    ]);
  });

  it('invites to add the first product when there are no departments either', async () => {
    await load([], []);

    expect(text(page().querySelector('.empty-state h3'))).toBe('No products yet');
    expect(cards().length).toBe(0);
  });

  it('shows why the products could not be loaded', async () => {
    fixture.detectChanges();
    http.expectOne('/api/products').flush(null, { status: 0, statusText: 'Unknown Error' });
    http.expectOne('/api/departments').flush([department()]);
    await fixture.whenStable();

    expect(text(page().querySelector('.banner'))).toBe(
      'The product list could not be loaded. The portal cannot be reached. Check your network connection and try again. Try again',
    );

    page().querySelector<HTMLButtonElement>('.banner button')!.click();
    fixture.detectChanges();
    http.expectOne('/api/products').flush([summary]);
    http.expectOne('/api/departments').flush([department()]);
    await fixture.whenStable();

    expect(page().querySelector('.banner')).toBeNull();
    expect(gridRows(page()).length).toBe(1);
  });

  it('searches as the user types and shows only the departments with matches', async () => {
    await load();
    expect(text(page().querySelector('.summary'))).toBe('1 product in 2 departments');

    const input = page().querySelector<HTMLInputElement>('input[aria-label="Search products"]')!;
    input.value = '  cert ';
    input.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 300));
    fixture.detectChanges();
    http.expectOne('/api/products?search=cert').flush([summary]);
    await fixture.whenStable();

    expect(cards().map((section) => text(section.querySelector('h2')))).toEqual([
      'Corporate Technology',
    ]);
    expect(text(page().querySelector('.summary'))).toBe('1 product in 1 department');

    input.value = 'payments';
    input.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 300));
    fixture.detectChanges();
    http.expectOne('/api/products?search=payments').flush([]);
    await fixture.whenStable();

    expect(text(page().querySelector('.empty-state h3'))).toBe('No product matches "payments"');
    expect(page().querySelector('.empty-state a')).toBeNull();
    expect(text(page().querySelector('.summary'))).toBe('0 products in 0 departments');

    page().querySelector<HTMLButtonElement>('.empty-state button')!.click();
    await new Promise((resolve) => setTimeout(resolve, 300));
    fixture.detectChanges();
    http.expectOne('/api/products').flush([summary]);
    await fixture.whenStable();

    expect(input.value).toBe('');
    expect(page().querySelector('.empty-state')).toBeNull();
  });

  it('shows a product without team or pipelines and opens it from its row', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const follow = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await load([
      {
        ...summary,
        id: 4,
        ownerTeam: null,
        description: null,
        pipelineCount: 0,
        activePipelineCount: 0,
      },
      { ...summary, id: 5, activePipelineCount: 3 },
    ]);
    const rows = gridRows(page());

    expect(text(gridCell(rows[0], 'ownerTeam'))).toBe('–');
    expect(rows[0].querySelector('.description')).toBeNull();
    expect(text(gridCell(rows[0], 'pipelines'))).toBe('None yet');
    expect(rows[1].querySelector('.revoked')).toBeNull();
    expect(text(rows[1].querySelector('.pipelines'))).toBe('3, all active');

    rows[0].querySelector<HTMLAnchorElement>('a.name')!.click();
    await settleGrid();
    expect(String(follow.mock.calls[0][0])).toBe('/admin/products/4');
    expect(navigate).not.toHaveBeenCalled();

    gridCell(rows[0], 'ownerTeam').click();
    await settleGrid();
    expect(navigate).toHaveBeenCalledWith(['/admin/products', 4]);
  });
});
