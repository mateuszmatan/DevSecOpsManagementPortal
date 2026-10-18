import { Injectable, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { UserApi } from './api';

/** The signed-in user; undefined while loading or when the portal cannot tell. */
@Injectable({ providedIn: 'root' })
export class CurrentUser {
  readonly profile = toSignal(
    inject(UserApi)
      .me()
      .pipe(catchError(() => of(undefined))),
  );
}
