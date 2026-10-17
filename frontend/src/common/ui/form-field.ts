import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Directive,
  DoCheck,
  ElementRef,
  computed,
  contentChild,
  inject,
  input,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormGroupDirective, NgControl, NgForm, Validators } from '@angular/forms';
import { Subscription, merge } from 'rxjs';

let nextId = 0;

export const uniqueId = (prefix: string) => `${prefix}-${nextId++}`;

/**
 * V6 Width Directive: Matches field width to expected data size rather than uniform widths.
 * xs: 120px (ports, numbers, small codes)
 * sm: 180px (tags, versions, short keys)
 * md: 320px (names, departments, identifiers)
 * lg: 480px (branch names, repo slugs)
 * full: 100% (URLs, descriptions, scripts)
 */
@Directive({
  selector: '[v6Width], [dsoWidth]',
  standalone: true,
  host: {
    '[style.width]': 'resolvedWidth()',
    '[style.max-width]': 'resolvedMaxWidth()',
    '[class.v6-field-xs]': "widthCategory() === 'xs'",
    '[class.v6-field-sm]': "widthCategory() === 'sm'",
    '[class.v6-field-md]': "widthCategory() === 'md'",
    '[class.v6-field-lg]': "widthCategory() === 'lg'",
    '[class.v6-field-full]': "widthCategory() === 'full'",
  },
})
export class V6WidthDirective {
  readonly v6Width = input<string | undefined>(undefined);
  readonly dsoWidth = input<string | undefined>(undefined);

  protected readonly widthCategory = computed(() => {
    const w = this.v6Width() || this.dsoWidth();
    if (!w) return 'full';
    if (['xs', 'sm', 'md', 'lg', 'full'].includes(w)) {
      return w;
    }
    return 'custom';
  });

  protected readonly resolvedWidth = computed(() => {
    const cat = this.widthCategory();
    switch (cat) {
      case 'xs':
        return '120px';
      case 'sm':
        return '180px';
      case 'md':
        return '320px';
      case 'lg':
        return '480px';
      case 'full':
        return '100%';
      default:
        return this.v6Width() || this.dsoWidth() || '100%';
    }
  });

  protected readonly resolvedMaxWidth = computed(() => {
    const cat = this.widthCategory();
    return cat === 'full' ? '100%' : this.resolvedWidth();
  });
}

@Directive({
  selector: 'input[dsoInput], textarea[dsoInput], select[dsoInput], input[v6Input], textarea[v6Input], select[v6Input]',
  standalone: true,
  host: {
    '[id]': 'id',
    '[class.form-control]': '!select',
    '[class.form-control-sm]': '!select && !large()',
    '[class.form-control-lg]': '!select && large()',
    '[class.form-select]': 'select',
    '[class.form-select-sm]': 'select && !large()',
    '[class.form-select-lg]': 'select && large()',
    '[class.is-invalid]': 'errorState()',
    '[attr.aria-invalid]': 'errorState() || null',
    '[attr.aria-describedby]': 'describedBy()',
    '(focus)': 'onFocus()',
    '(blur)': 'onBlur()',
  },
})
export class DsoInput implements DoCheck {
  readonly id = uniqueId('dso-input');
  readonly large = input<boolean>(false);
  readonly element =
    inject<ElementRef<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>>(ElementRef)
      .nativeElement;
  readonly ngControl = inject(NgControl, { optional: true, self: true });

  protected readonly select = this.element.tagName === 'SELECT';

  private readonly form =
    inject(FormGroupDirective, { optional: true }) ?? inject(NgForm, { optional: true });
  private readonly revision = signal(0);
  private watched: AbstractControl | null = null;
  private subscription?: Subscription;
  private snapshot = '';

  private readonly field = inject(DsoFormField, { optional: true });

  readonly focused = signal(false);

  readonly errorState = computed(() => {
    this.revision();
    const control = this.ngControl?.control;
    return !!control && control.invalid && (control.touched || !!this.form?.submitted);
  });

  protected readonly describedBy = computed(() =>
    this.field ? (this.errorState() ? this.field.errorId : this.field.hintId) : null,
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => this.subscription?.unsubscribe());
  }

  onFocus(): void {
    this.focused.set(true);
  }

  onBlur(): void {
    this.focused.set(false);
  }

  ngDoCheck(): void {
    const control = this.ngControl?.control ?? null;
    if (control !== this.watched) {
      this.watched = control;
      this.subscription?.unsubscribe();
      this.subscription = control
        ? merge(control.events, this.form?.ngSubmit ?? []).subscribe(() => this.changed())
        : undefined;
    }
    const snapshot = `${control?.status}|${control?.touched}`;
    if (snapshot !== this.snapshot) {
      this.snapshot = snapshot;
      this.changed();
    }
  }

  required(): boolean {
    this.revision();
    return this.element.required || !!this.ngControl?.control?.hasValidator(Validators.required);
  }

  private changed(): void {
    this.revision.update((value) => value + 1);
  }
}

@Component({
  selector: 'dso-label, v6-label',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ng-content />',
})
export class DsoLabel {}

@Component({
  selector: 'dso-hint, v6-hint',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ng-content />',
})
export class DsoHint {}

@Component({
  selector: 'dso-error, v6-error',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ng-content />',
})
export class DsoError {}

@Directive({
  selector: '[dsoSuffix], [v6Suffix]',
  standalone: true,
  host: { class: 'dso-suffix' },
})
export class DsoSuffix {}

/**
 * Contextual help icon with hover/focus tooltip according to V6 guidelines.
 */
@Component({
  selector: 'v6-help-icon, dso-help-icon',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="v6-help-icon-badge"
      tabindex="0"
      role="tooltip"
      [attr.aria-label]="text()"
      [title]="text()"
    >
      ?
    </span>
  `,
  styles: `
    :host {
      display: inline-flex;
      vertical-align: middle;
      margin-left: 4px;
    }
    .v6-help-icon-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 16px;
      height: 16px;
      border-radius: 50%;
      background-color: #cbd5e1;
      color: #334155;
      font-size: 11px;
      font-weight: bold;
      cursor: help;
      outline: none;
      transition: background-color 0.15s, color 0.15s;
    }
    .v6-help-icon-badge:hover, .v6-help-icon-badge:focus {
      background-color: #0284c7;
      color: #ffffff;
    }
  `,
})
export class V6HelpIconComponent {
  readonly text = input.required<string>();
}

/**
 * Constantly visible process explanation component.
 */
@Component({
  selector: 'v6-process-hint, dso-process-hint',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="v6-process-hint-box">
      <span class="v6-process-hint-icon" aria-hidden="true">&#9432;</span>
      <div class="v6-process-hint-text">
        <ng-content />
      </div>
    </div>
  `,
  styles: `
    .v6-process-hint-box {
      display: flex;
      align-items: flex-start;
      gap: 6px;
      margin-top: 4px;
      margin-bottom: 4px;
      padding: 6px 10px;
      background-color: #f8fafc;
      border-left: 3px solid #0284c7;
      border-radius: 2px;
      font-size: 11.5px;
      line-height: 1.4;
      color: #475569;
    }
    .v6-process-hint-icon {
      color: #0284c7;
      font-weight: bold;
      font-size: 13px;
    }
  `,
})
export class V6ProcessHintComponent {}

/**
 * Focus-triggered instructional helper component.
 */
@Component({
  selector: 'v6-focus-hint, dso-focus-hint',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <div class="v6-focus-hint-box">
        <ng-content />
      </div>
    }
  `,
  styles: `
    .v6-focus-hint-box {
      margin-top: 3px;
      font-size: 11px;
      color: #0369a1;
      font-style: italic;
    }
  `,
})
export class V6FocusHintComponent {
  private readonly field = inject(DsoFormField, { optional: true });
  protected readonly visible = computed(() => this.field?.focused() ?? false);
}

/**
 * Real-time character limit countdown component.
 */
@Component({
  selector: 'v6-char-counter, dso-char-counter',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="v6-char-counter"
      [class.v6-char-limit-near]="isNearLimit()"
      [class.v6-char-limit-exceeded]="isExceeded()"
    >
      {{ count() }} / {{ max() }}
    </span>
  `,
  styles: `
    .v6-char-counter {
      font-size: 11px;
      color: #94a3b8;
    }
    .v6-char-limit-near {
      color: #d97706;
      font-weight: 600;
    }
    .v6-char-limit-exceeded {
      color: #dc2626;
      font-weight: bold;
    }
  `,
})
export class V6CharCounterComponent {
  readonly count = input.required<number>();
  readonly max = input.required<number>();

  protected readonly isNearLimit = computed(() => {
    const diff = this.max() - this.count();
    return diff <= 10 && diff >= 0;
  });

  protected readonly isExceeded = computed(() => this.count() > this.max());
}

/**
 * Full page / modal submit loading overlay.
 */
@Component({
  selector: 'v6-submit-overlay, dso-submit-overlay',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (active()) {
      <div class="v6-submit-overlay-backdrop" role="status" aria-live="polite">
        <div class="v6-submit-spinner-card">
          <div class="spinner-border text-primary v6-submit-spinner" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <span class="v6-submit-message">{{ message() }}</span>
        </div>
      </div>
    }
  `,
  styles: `
    .v6-submit-overlay-backdrop {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(255, 255, 255, 0.75);
      backdrop-filter: blur(1px);
      z-index: 1060;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: inherit;
    }
    .v6-submit-spinner-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      padding: 20px 28px;
      background: white;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      box-shadow: 0 4px 16px rgba(0,0,0,0.1);
    }
    .v6-submit-spinner {
      width: 2.2rem;
      height: 2.2rem;
    }
    .v6-submit-message {
      font-size: 13px;
      font-weight: 500;
      color: #1e293b;
    }
  `,
})
export class V6SubmitOverlayComponent {
  readonly active = input<boolean>(false);
  readonly message = input<string>('Submitting, please wait...');
}

@Component({
  selector: 'dso-form-field, v6-form-field',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  host: {
    class: 'dso-form-field',
    '[class.has-error]': 'errorState()',
    '[class.has-suffix]': '!!suffix()',
  },
  template: `
    <label class="form-label" [attr.for]="input()?.id">
      <ng-content select="dso-label, v6-label" />
      @if (input()?.required()) {
        <span class="required-marker" aria-hidden="true"></span>
      }
    </label>
    <div class="control">
      <ng-content />
      <ng-content select="[dsoSuffix], [v6Suffix]" />
    </div>
    @if (errorState()) {
      <div class="invalid-feedback d-block" [id]="errorId">
        <ng-content select="dso-error, v6-error" />
      </div>
    } @else {
      <div class="form-text" [id]="hintId">
        <ng-content select="dso-hint, v6-hint" />
      </div>
    }
  `,
})
export class DsoFormField {
  protected readonly input = contentChild(DsoInput);
  protected readonly suffix = contentChild(DsoSuffix);
  readonly hintId = uniqueId('dso-hint');
  readonly errorId = uniqueId('dso-error');
  readonly focused = computed(() => this.input()?.focused() ?? false);
  protected readonly errorState = computed(() => this.input()?.errorState() ?? false);
}

export const FORM_FIELD = [
  DsoFormField,
  DsoLabel,
  DsoHint,
  DsoError,
  DsoInput,
  DsoSuffix,
  V6WidthDirective,
  V6HelpIconComponent,
  V6ProcessHintComponent,
  V6FocusHintComponent,
  V6CharCounterComponent,
  V6SubmitOverlayComponent,
] as const;

export const V6_FORM_FIELD = FORM_FIELD;
