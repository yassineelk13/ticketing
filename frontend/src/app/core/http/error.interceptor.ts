import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../notification/notification.service';
import { SILENT_ERRORS } from './api-context';
import { toUserMessage } from './problem';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const notifications = inject(NotificationService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const sessionExpired = error.status === 401 && auth.isAuthenticated();
        if (sessionExpired) {
          auth.logout();
          void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
        }
        if (!req.context.get(SILENT_ERRORS)) {
          notifications.error(
            sessionExpired
              ? 'Votre session a expiré. Veuillez vous reconnecter.'
              : toUserMessage(error.status),
          );
        }
      }
      return throwError(() => error);
    }),
  );
};
