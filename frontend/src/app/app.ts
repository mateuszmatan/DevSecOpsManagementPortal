import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AppShell } from '@common/shell/app-shell';
import { CurrentUser } from './core/current-user';
import { SECTIONS } from './core/sections';

@Component({
  selector: 'dso-root',
  imports: [AppShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<dso-app-shell [sections]="sections" [user]="user()" />',
})
export class App {
  protected readonly sections = SECTIONS;
  protected readonly user = inject(CurrentUser).profile;
}
