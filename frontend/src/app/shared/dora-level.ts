import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DoraLevel } from '../core/models';

@Component({
  selector: 'dso-dora-level',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (level(); as value) {
    <span class="chip level" [class]="value.toLowerCase()">{{
      value.charAt(0) + value.slice(1).toLowerCase()
    }}</span>
  }`,
})
export class DoraLevelBadge {
  readonly level = input<DoraLevel | null>(null);
}
