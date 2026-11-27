import { Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { NotificationService } from './core/notification/notification.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  constructor() {
    this.auth.sessionExpired$.pipe(takeUntilDestroyed()).subscribe(() => {
      this.notifications.error('Votre session a expiré. Veuillez vous reconnecter.');
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
    });
  }

  protected logout(): void {
    this.auth.logout();
    void this.router.navigate(['/events']);
  }
}
