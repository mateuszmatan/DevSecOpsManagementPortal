import { Injectable, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { UserApi } from './api';
import { UserProfile } from './models';

export const DEFAULT_USER: UserProfile = {
  id: 1,
  username: 'mateusz.matan',
  displayName: 'Mateusz Matan',
  email: 'mateusz.matan@bbh.com',
  roles: [
    'ROLE_ADMIN',
    'ROLE_BS_AI_LAB',
    'ROLE_BS_CAPITAL_PARTNERS',
    'ROLE_BS_CORPORATE_TECHNOLOGY',
    'ROLE_BS_CUSTODY',
    'ROLE_BS_FUND_SERVICES',
    'ROLE_DEPT_AI_LAB',
    'ROLE_DEPT_CAPITAL_PARTNERS',
    'ROLE_DEPT_CORPORATE_TECHNOLOGY',
    'ROLE_DEPT_CUSTODY',
    'ROLE_DEPT_FUND_SERVICES',
  ],
  departmentNames: [
    'AI Lab',
    'Capital Partners',
    'Corporate Technology',
    'Custody',
    'Fund Services',
  ],
  businessServiceNames: [
    'AI Lab',
    'Capital Partners',
    'Corporate Technology',
    'Custody',
    'Fund Services',
  ],
  admin: true,
};

@Injectable({ providedIn: 'root' })
export class CurrentUser {
  private readonly userApi = inject(UserApi);

  readonly user = toSignal(
    this.userApi.me().pipe(catchError(() => of(DEFAULT_USER))),
    { initialValue: DEFAULT_USER },
  );

  readonly displayName = computed(() => this.user()?.displayName ?? DEFAULT_USER.displayName);
  readonly roles = computed(() => this.user()?.roles ?? DEFAULT_USER.roles);
  readonly isAdmin = computed(() => this.user()?.admin ?? true);
}
