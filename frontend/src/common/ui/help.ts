import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  afterRenderEffect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { AriaDescriber, FocusMonitor } from '@angular/cdk/a11y';
import { CdkConnectedOverlay, CdkOverlayOrigin, ConnectedPosition } from '@angular/cdk/overlay';
import { SvgIconComponent } from 'angular-svg-icon';

const POSITIONS: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 4 },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -4 },
  { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 4 },
];

const HOVER_GRACE_MS = 150;

/** The label of the form field or check box around the icon, without its config key chips. */
function fieldLabel(host: HTMLElement): string {
  const label = host.closest('.dso-form-field, .dso-checkbox')?.querySelector('label');
  const copy = label?.cloneNode(true) as HTMLElement | undefined;
  copy?.querySelectorAll('code').forEach((code) => code.remove());
  return (copy?.textContent ?? '')
    .replace(/\(\s*\)/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * An info icon whose help opens on hover or on keyboard focus, never on a click or a focus set by code, such as a
 * dialog's first focus. Help opened by the keyboard stays until the focus leaves. The icon is named after the field
 * it belongs to: the label input, or else the label of the surrounding form field or check box.
 */
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
  readonly label = input<string>();

  protected readonly open = signal(false);
  protected readonly positions = POSITIONS;
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private focused = false;
  private timer?: ReturnType<typeof setTimeout>;

  constructor() {
    const describer = inject(AriaDescriber);
    const focusMonitor = inject(FocusMonitor);
    const destroyRef = inject(DestroyRef);
    afterRenderEffect((onCleanup) => {
      const element = this.trigger().nativeElement;
      const text = this.text();
      const label = this.label() ?? fieldLabel(this.host);
      element.setAttribute(
        'aria-label',
        label ? `More information about ${label}` : 'More information',
      );
      describer.describe(element, text);
      onCleanup(() => describer.removeDescription(element, text));
    });
    afterNextRender(() => {
      const element = this.trigger().nativeElement;
      focusMonitor.monitor(element).subscribe((origin) => {
        if (origin === 'keyboard') {
          this.focused = true;
          this.show();
        } else if (origin === null) {
          this.focused = false;
          this.hide();
        }
      });
      destroyRef.onDestroy(() => focusMonitor.stopMonitoring(element));
    });
    destroyRef.onDestroy(() => clearTimeout(this.timer));
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
    if (!this.focused) {
      this.timer = setTimeout(() => this.open.set(false), HOVER_GRACE_MS);
    }
  }
}
