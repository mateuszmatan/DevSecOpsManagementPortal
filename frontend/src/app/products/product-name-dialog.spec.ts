import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { choose, inputOf, optionsOf, selectOf, text } from '@common/testing/dom';
import { department } from '../testing/fixtures';
import { ProductNameDialog, ProductNameDialogData } from './product-name-dialog';

describe('ProductNameDialog', () => {
  let fixture: ComponentFixture<ProductNameDialog>;
  const close = vi.fn();
  const departments = [department(), department({ id: 5, name: 'Fund Services' })];

  async function render(departmentId: number | null = null) {
    TestBed.configureTestingModule({
      imports: [ProductNameDialog],
      providers: [
        {
          provide: DIALOG_DATA,
          useValue: { departments, departmentId } satisfies ProductNameDialogData,
        },
        { provide: DialogRef, useValue: { close } },
      ],
    });
    fixture = TestBed.createComponent(ProductNameDialog);
    await fixture.whenStable();
  }

  afterEach(() => close.mockReset());

  const page = () => fixture.nativeElement as HTMLElement;
  const departmentId = () => fixture.componentInstance['form'].controls.departmentId;

  async function submit(name: string) {
    const input = inputOf(page(), 'Product name');
    input.value = name;
    input.dispatchEvent(new Event('input'));
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('continues with the department and the trimmed name', async () => {
    await render(5);
    expect(departmentId().value).toBe(5);

    await submit('  CertScanner ');

    expect(close).toHaveBeenCalledWith({ name: 'CertScanner', departmentId: 5 });
  });

  it('asks for the department first, ignoring an unknown one', async () => {
    await render(42);

    await submit('CertScanner');

    expect(departmentId().value).toBeNull();
    expect(close).not.toHaveBeenCalled();
    expect(text(page().querySelector('dso-error'))).toBe('Required');

    expect(optionsOf(selectOf(page(), 'Department'))).toEqual([
      'Corporate Technology',
      'Fund Services',
    ]);
    choose(selectOf(page(), 'Department'), 'Corporate Technology');
    await submit('CertScanner');

    expect(close).toHaveBeenCalledWith({ name: 'CertScanner', departmentId: 3 });
  });

  it('asks again when the name is blank', async () => {
    await render(3);

    await submit('   ');

    expect(close).not.toHaveBeenCalled();
    expect(page().querySelector('dso-error')?.textContent).toBe('Required');
  });
});
