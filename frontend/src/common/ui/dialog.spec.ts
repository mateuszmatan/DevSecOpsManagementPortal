import { InteractivityChecker } from '@angular/cdk/a11y';
import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, Injectable } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DIALOG, provideDialogs } from './dialog';
import { FORM_FIELD } from './form-field';

@Component({
  imports: [DIALOG],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<h2 dsoDialogTitle>Delete the pipeline?</h2>
    <button [dsoDialogClose]="true">Delete pipeline</button>`,
})
class Question {}

@Component({
  imports: [DIALOG, FORM_FIELD],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<h2 dsoDialogTitle>Add product</h2>
    <dso-form-field help="The full name of the product.">
      <dso-label>Name</dso-label>
      <input dsoInput />
    </dso-form-field>`,
})
class NameForm {}

/** jsdom lays nothing out, so the focus trap would find no visible element to focus. */
@Injectable()
class LaidOutChecker extends InteractivityChecker {
  override isVisible(): boolean {
    return true;
  }
}

describe('provideDialogs', () => {
  let ref: DialogRef<boolean, Question>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideDialogs(), { provide: InteractivityChecker, useClass: LaidOutChecker }],
    });
    ref = TestBed.inject(Dialog).open<boolean, unknown, Question>(Question);
    TestBed.tick();
    await Promise.resolve();
  });

  afterEach(() => TestBed.inject(Dialog).closeAll());

  const pane = () => ref.overlayRef.overlayElement;

  it('opens modal dialogs over a backdrop that close when the reader navigates away', () => {
    expect(ref.config.hasBackdrop).toBe(true);
    expect(ref.config.closeOnNavigation).toBe(true);
    expect(ref.config.panelClass).toBe('dso-dialog');
    expect(ref.overlayRef.backdropElement).not.toBeNull();
    expect(pane().querySelector('[role="dialog"]')).not.toBeNull();
  });

  it('closes with the result of the close button', async () => {
    const closed = new Promise((resolve) => ref.closed.subscribe(resolve));

    pane().querySelector('button')?.click();

    expect(await closed).toBe(true);
  });

  it('starts a form on its first field, not on the help icon before it, and shows no help', async () => {
    ref.close();
    const form = TestBed.inject(Dialog).open(NameForm);
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));

    expect(document.activeElement).toBe(form.overlayRef.overlayElement.querySelector('input'));
    expect(document.querySelector('.dso-help-popover')).toBeNull();
  });
});
