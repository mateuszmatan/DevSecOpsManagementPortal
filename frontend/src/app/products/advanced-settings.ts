import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { PANEL } from '@common/ui/panel';

@Component({
  selector: 'dso-advanced-settings',
  imports: [PANEL],
  changeDetection: ChangeDetectionStrategy.Eager,
  host: { class: 'span-12' },
  template: `
    <dso-panel
      #panel="dsoPanel"
      class="advanced"
      [expanded]="open() || attention()"
      (expandedChange)="open.set($event)"
      [class.has-errors]="attention()"
    >
      <dso-panel-header>
        <span class="advanced-title">Advanced settings</span>
        <span class="panel-description">{{ summary() }}</span>
        <span class="panel-toggle" aria-hidden="true">{{
          panel.expanded() ? 'Hide' : 'Show'
        }}</span>
      </dso-panel-header>
      <div class="form-fields">
        <ng-content />
      </div>
    </dso-panel>
  `,
  styles: `
    :host {
      display: block;
      margin-top: 10px;
    }

    .advanced {
      margin: 0;
      background: #fafbfc;
    }

    .advanced-title {
      flex-shrink: 0;
      font-weight: 600;
    }
  `,
})
export class AdvancedSettings {
  readonly summary = input.required<string>();
  readonly controls = input<readonly AbstractControl[]>([]);
  readonly submitted = input(false);

  protected readonly open = signal(false);

  protected attention(): boolean {
    return this.controls().some(
      (control) => control.invalid && (control.touched || this.submitted()),
    );
  }
}
