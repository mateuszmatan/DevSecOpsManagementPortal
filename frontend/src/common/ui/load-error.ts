import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { errorMessage } from '../core/errors';

@Component({
  selector: 'dso-load-error',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'banner danger', role: 'alert' },
  template: `
    <span class="banner-text">{{ what() }} could not be loaded. {{ reason() }}</span>
    <button type="button" class="btn btn-outline-primary" (click)="retry.emit()">Try again</button>
    <ng-content />
  `,
})
export class LoadError {
  readonly what = input.required<string>();
  readonly error = input.required<unknown>();
  readonly retry = output();

  protected readonly reason = computed(() => errorMessage(this.error()));
}
