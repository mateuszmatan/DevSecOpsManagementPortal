import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Fields, check, line } from '../shared/fields';
import { buttonOf, fieldOf, text } from '../testing/dom';
import { FORM_FIELD } from './form-field';

@Component({
  imports: [ReactiveFormsModule, FORM_FIELD, Fields],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dso-form-field help="The team that looks after the product.">
      <dso-label>Owner team</dso-label>
      <input dsoInput [formControl]="group.controls.team" />
    </dso-form-field>
    <dso-form-field>
      <dso-label>Plain</dso-label>
      <input dsoInput [formControl]="group.controls.plain" />
    </dso-form-field>
    <dso-fields [group]="group" [fields]="fields" />
  `,
})
class Host {
  readonly group = new FormGroup({
    team: new FormControl(''),
    plain: new FormControl(''),
    name: new FormControl(''),
    enabled: new FormControl(false),
  });
  readonly fields = [
    line('name', 'Service name', '', 6, { help: 'Used in the job names.' }),
    check('enabled', 'Write metrics', 'influx.enabled', 6, { help: 'Sends the results.' }),
  ];
}

describe('DsoHelp', () => {
  let fixture: ComponentFixture<Host>;

  beforeEach(async () => {
    fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const trigger = (root: ParentNode) => buttonOf(root, 'More information');
  const popover = () => document.querySelector<HTMLElement>('.dso-help-popover');
  const description = (button: HTMLElement) =>
    (button.getAttribute('aria-describedby') ?? '')
      .split(' ')
      .map((id) => text(document.getElementById(id)))
      .join(' ');

  async function fire(element: HTMLElement, type: string) {
    element.dispatchEvent(new Event(type));
    await fixture.whenStable();
  }

  async function pause() {
    await new Promise((resolve) => setTimeout(resolve, 200));
    await fixture.whenStable();
  }

  it('puts an info icon after the label of the fields that have help, and none on the others', () => {
    const field = fieldOf(page(), 'Owner team')!;

    expect(field.querySelector('.form-label-row .form-label + dso-help')).not.toBeNull();
    expect(fieldOf(page(), 'Plain')!.querySelector('dso-help')).toBeNull();
    expect(text(field.querySelector('label'))).toBe('Owner team');
    expect(popover()).toBeNull();
  });

  it('describes the icon with the help text for assistive technology', () => {
    const button = trigger(fieldOf(page(), 'Owner team')!);

    expect(button.type).toBe('button');
    expect(description(button)).toBe('The team that looks after the product.');
  });

  it('shows the help on hover and keyboard focus, and Escape closes it', async () => {
    const button = trigger(fieldOf(page(), 'Owner team')!);

    await fire(button, 'mouseenter');

    expect(popover()?.getAttribute('role')).toBe('tooltip');
    expect(text(popover())).toBe('The team that looks after the product.');

    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27 }));
    await fixture.whenStable();

    expect(popover()).toBeNull();

    await fire(button, 'focus');

    expect(text(popover())).toBe('The team that looks after the product.');

    await fire(button, 'blur');

    expect(popover()).toBeNull();
  });

  it('keeps the help open while the pointer moves onto it', async () => {
    const button = trigger(fieldOf(page(), 'Owner team')!);
    await fire(button, 'mouseenter');
    await fire(button, 'mouseleave');
    await fire(popover()!, 'mouseenter');
    await pause();

    expect(popover()).not.toBeNull();

    await fire(popover()!, 'mouseleave');
    await pause();

    expect(popover()).toBeNull();
  });

  it('renders the help of fields and check boxes listed for dso-fields the same way', () => {
    const name = trigger(fieldOf(page(), 'Service name')!);
    const box = page().querySelector('dso-checkbox')!;

    expect(description(name)).toBe('Used in the job names.');
    expect(box.querySelector('.form-check-label + dso-help')).not.toBeNull();
    expect(description(trigger(box))).toBe('Sends the results.');
    expect(text(box.querySelector('label'))).toBe('Write metrics (influx.enabled)');
  });
});
