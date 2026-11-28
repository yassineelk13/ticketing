import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { catchError, finalize, map, of, Subject, switchMap, tap } from 'rxjs';
import { toUserMessage } from '../../../core/http/problem';
import { ROLE_LABELS, UserResponse } from '../../../core/models/user.model';
import { NotificationService } from '../../../core/notification/notification.service';
import { applyServerErrors, fieldError } from '../../../shared/forms/form-errors';
import { UserService } from '../user.service';

type LoadState = 'loading' | 'ready' | 'error';

interface LoadResult {
  profile: UserResponse | null;
  failed: boolean;
}

@Component({
  selector: 'app-profile',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly users = inject(UserService);
  private readonly notifications = inject(NotificationService);
  private readonly reloads = new Subject<void>();

  protected readonly state = signal<LoadState>('loading');
  protected readonly profile = signal<UserResponse | null>(null);
  protected readonly saving = signal(false);
  protected readonly form = this.fb.group({
    firstName: ['', [Validators.required, Validators.pattern(/\S/)]],
    lastName: ['', [Validators.required, Validators.pattern(/\S/)]],
  });
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly fieldError = fieldError;
  protected readonly requiredMessages = {
    required: 'Ce champ est obligatoire.',
    pattern: 'Ce champ est obligatoire.',
  };

  constructor() {
    this.reloads
      .pipe(
        tap(() => this.state.set('loading')),
        switchMap(() =>
          this.users.me().pipe(
            map((profile): LoadResult => ({ profile, failed: false })),
            catchError(() => of<LoadResult>({ profile: null, failed: true })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ profile, failed }) => {
        this.profile.set(profile);
        if (profile) {
          this.resetForm(profile);
        }
        this.state.set(failed ? 'error' : 'ready');
      });

    this.reloads.next();
  }

  protected reload(): void {
    this.reloads.next();
  }

  protected save(): void {
    if (this.saving()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { firstName, lastName } = this.form.getRawValue();
    this.saving.set(true);
    this.users
      .updateMe({ firstName: firstName.trim(), lastName: lastName.trim() })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (profile) => {
          this.profile.set(profile);
          this.resetForm(profile); // the saved values become the new reference (form pristine again)
          this.notifications.success('Profil mis à jour.');
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 400 && applyServerErrors(this.form, error)) {
            return;
          }
          this.notifications.error(toUserMessage(error.status));
        },
      });
  }

  private resetForm(profile: UserResponse): void {
    this.form.reset({ firstName: profile.firstName, lastName: profile.lastName });
  }
}
