import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { PipelineRun } from '../core/models';

@Component({
  selector: 'dso-build-link',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let r = run();
    @if (r.buildUrl) {
      <a
        class="build-link"
        [href]="r.buildUrl"
        target="_blank"
        rel="noopener"
        (click)="$event.stopPropagation()"
        >#{{ r.build }}</a
      >
    } @else {
      {{ r.build === null ? empty() : '#' + r.build }}
    }
  `,
})
export class BuildLink {
  readonly run = input.required<Pick<PipelineRun, 'build' | 'buildUrl'>>();
  readonly empty = input('–');
}
