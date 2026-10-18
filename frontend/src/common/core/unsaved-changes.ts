import { inject } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { CanDeactivateFn } from '@angular/router';
import { askToConfirm } from '../shared/confirm-dialog';

export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
}

export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (page) => {
  if (!page.hasUnsavedChanges()) {
    return true;
  }
  return askToConfirm(inject(Dialog), {
    title: 'Discard your changes?',
    message: 'The changes on this page have not been saved.',
    confirmLabel: 'Discard',
    danger: true,
  });
};
