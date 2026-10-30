import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { ServiceSectionId } from './product-form-model';

/** A field of a service that needs input, named by its label or, without one, by its section. */
export interface MissingField {
  section: ServiceSectionId;
  label: string;
  control: AbstractControl;
}

/** The fields that need input, each a link that opens its section and puts the cursor in it. */
@Component({
  selector: 'dso-missing-fields',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (field of missing(); track field.control; let last = $last) {
      <button type="button" class="text-link" (click)="open.emit(field)">{{ field.label }}</button>
      @if (!last) {
        <span>, </span>
      }
    }
  `,
  styles: `
    .text-link {
      font-size: inherit;
      text-decoration: underline;
    }
  `,
})
export class MissingFields {
  readonly missing = input.required<readonly MissingField[]>();
  readonly open = output<MissingField>();
}
