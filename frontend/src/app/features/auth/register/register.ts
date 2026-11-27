import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { toUserMessage } from '../../../core/http/problem';
import { Role } from '../../../core/models/user.model';
import { NotificationService } from '../../../core/notification/notification.service';
import { applyServerErrors, fieldError } from '../../../shared/forms/form-errors';

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
  ],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly notifications = inject(NotificationService);

  protected readonly returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
  protected readonly form = this.fb.group({
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    role: this.fb.control<Role>('USER', Validators.required),
  });
  protected readonly submitting = signal(false);

  protected readonly roles: { value: Role; label: string }[] = [
    { value: 'USER', label: 'Participant : réserver des billets' },
    { value: 'ORGANIZER', label: 'Organisateur : créer et gérer des événements' },
  ];

  protected readonly fieldError = fieldError;
  protected readonly requiredMessage = { required: 'Ce champ est obligatoire.' };
  protected readonly emailMessages = {
    required: 'L’adresse e-mail est obligatoire.',
    email: 'Adresse e-mail invalide.',
  };
  protected readonly passwordMessages = {
    required: 'Le mot de passe est obligatoire.',
    minlength: 'Au moins 8 caractères.',
    maxlength: 'Au plus 72 caractères.',
  };

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => {
        this.notifications.success('Compte créé. Vous pouvez maintenant vous connecter.');
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        void this.router.navigate(['/login'], { queryParams: returnUrl ? { returnUrl } : {} });
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        if (error.status === 409) {
          const email = this.form.controls.email;
          email.setErrors({ server: 'Cette adresse e-mail est déjà utilisée.' });
          email.markAsTouched();
        } else if (!(error.status === 400 && applyServerErrors(this.form, error))) {
          this.notifications.error(toUserMessage(error.status));
        }
      },
    });
  }
}
