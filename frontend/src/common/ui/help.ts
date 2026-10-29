import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterRenderEffect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { AriaDescriber } from '@angular/cdk/a11y';
import { CdkConnectedOverlay, CdkOverlayOrigin, ConnectedPosition } from '@angular/cdk/overlay';
import { SvgIconComponent } from 'angular-svg-icon';

const POSITIONS: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 4 },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -4 },
  { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 4 },
];

const HOVER_GRACE_MS = 150;

@Component({
  selector: 'dso-help',
  imports: [CdkConnectedOverlay, CdkOverlayOrigin, SvgIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'dso-help' },
  template: `
    <button
      #trigger
      type="button"
      class="dso-help-trigger"
      aria-label="More information"
      cdkOverlayOrigin
      #origin="cdkOverlayOrigin"
      (mouseenter)="show()"
      (mouseleave)="leave()"
      (focus)="show()"
      (blur)="hide()"
    >
      <svg-icon name="info" aria-hidden="true" />
    </button>
    <ng-template
      cdkConnectedOverlay
      [cdkConnectedOverlayOrigin]="origin"
      [cdkConnectedOverlayOpen]="open()"
      [cdkConnectedOverlayPositions]="positions"
      (detach)="hide()"
    >
      <div class="dso-help-popover" role="tooltip" (mouseenter)="show()" (mouseleave)="leave()">
        {{ text() }}
      </div>
    </ng-template>
  `,
})
export class DsoHelp {
  readonly text = input.required<string>();

  protected readonly open = signal(false);
  protected readonly positions = POSITIONS;
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private timer?: ReturnType<typeof setTimeout>;

  constructor() {
    const describer = inject(AriaDescriber);
    afterRenderEffect((onCleanup) => {
      const element = this.trigger().nativeElement;
      const text = this.text();
      describer.describe(element, text);
      onCleanup(() => describer.removeDescription(element, text));
    });
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
  }

  protected show(): void {
    clearTimeout(this.timer);
    this.open.set(true);
  }

  protected hide(): void {
    clearTimeout(this.timer);
    this.open.set(false);
  }

  protected leave(): void {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.open.set(false), HOVER_GRACE_MS);
  }
}
