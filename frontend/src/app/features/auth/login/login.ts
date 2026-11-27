import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { safeReturnUrl } from '../../../core/auth/return-url';
import { toUserMessage } from '../../../core/http/problem';
import { NotificationService } from '../../../core/notification/notification.service';
import { fieldError } from '../../../shared/forms/form-errors';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly notifications = inject(NotificationService);

  protected readonly returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });
  protected readonly submitting = signal(false);
  protected readonly loginError = signal<string | null>(null);

  protected readonly fieldError = fieldError;
  protected readonly emailMessages = {
    required: 'L’adresse e-mail est obligatoire.',
    email: 'Adresse e-mail invalide.',
  };
  protected readonly passwordMessages = { required: 'Le mot de passe est obligatoire.' };

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.loginError.set(null);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        void this.router.navigateByUrl(safeReturnUrl(this.returnUrl));
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        if (error.status === 401) {
          this.loginError.set('E-mail ou mot de passe incorrect.');
        } else {
          this.notifications.error(toUserMessage(error.status));
        }
      },
    });
  }
}
