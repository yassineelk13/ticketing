import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { toUserMessage } from '../http/problem';
import { Role } from '../models/user.model';
import { NotificationService } from '../notification/notification.service';
import { AuthService } from './auth.service';

/** Requires a logged-in user; otherwise goes to /login and comes back afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).isAuthenticated()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** For the login/register pages: a logged-in user has no reason to see them. */
export const guestGuard: CanActivateFn = () => {
  if (!inject(AuthService).isAuthenticated()) {
    return true;
  }
  return inject(Router).createUrlTree(['/events']);
};

/** Requires a given role. The backend still enforces it (403): this is only for the UX. */
export function roleGuard(role: Role): CanActivateFn {
  return (_route, state) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isAuthenticated()) {
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
    }
    if (auth.role() === role) {
      return true;
    }
    inject(NotificationService).error(toUserMessage(403));
    return router.createUrlTree(['/events']);
  };
}
