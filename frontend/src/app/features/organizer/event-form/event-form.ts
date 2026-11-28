import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import {
  FormArray,
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { catchError, finalize, map, merge, of, Subject, switchMap, tap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { toUserMessage } from '../../../core/http/problem';
import {
  EVENT_TYPE_LABELS,
  EventRequest,
  EventResponse,
  EventType,
} from '../../../core/models/event.model';
import { NotificationService } from '../../../core/notification/notification.service';
import { applyServerErrors, fieldError } from '../../../shared/forms/form-errors';
import { EventService } from '../../events/event.service';
import {
  AttributeRow,
  buildAttributes,
  futureDate,
  integerValidator,
  toAttributeRows,
  toIsoUtc,
  toLocalInput,
  uniqueAttributeKeys,
} from '../event-form-utils';

type LoadState = 'loading' | 'ready' | 'notFound' | 'forbidden' | 'error';
type AttributeGroup = FormGroup<{ key: FormControl<string>; value: FormControl<string> }>;

interface LoadResult {
  event: EventResponse | null;
  state: LoadState;
}

@Component({
  selector: 'app-event-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './event-form.html',
  styleUrl: './event-form.scss',
})
export class EventForm {
  /** Bound from the :id route parameter. Absent on /organizer/events/new. */
  readonly id = input<string>();

  private readonly fb = inject(NonNullableFormBuilder);
  private readonly eventService = inject(EventService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly retries = new Subject<void>();
  private original: EventResponse | null = null;

  protected readonly isEdit = computed(() => this.id() !== undefined);
  protected readonly state = signal<LoadState>('loading');
  protected readonly saving = signal(false);

  protected readonly form = this.fb.group({
    title: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(150)]],
    description: ['', [Validators.maxLength(2000)]],
    type: this.fb.control<EventType>('CONCERT', Validators.required),
    city: ['', [Validators.required, Validators.pattern(/\S/)]],
    venue: ['', [Validators.required, Validators.pattern(/\S/)]],
    startDate: ['', [Validators.required, futureDate()]],
    totalSeats: this.fb.control<number | null>(null, [
      Validators.required,
      Validators.min(1),
      integerValidator,
    ]),
    attributes: new FormArray<AttributeGroup>([], uniqueAttributeKeys),
  });

  protected readonly types = Object.keys(EVENT_TYPE_LABELS) as EventType[];
  protected readonly typeLabels = EVENT_TYPE_LABELS;
  protected readonly fieldError = fieldError;
  protected readonly titleMessages = {
    required: 'Le titre est obligatoire.',
    pattern: 'Le titre est obligatoire.',
    maxlength: 'Au plus 150 caractères.',
  };
  protected readonly descriptionMessages = { maxlength: 'Au plus 2000 caractères.' };
  protected readonly requiredMessages = {
    required: 'Ce champ est obligatoire.',
    pattern: 'Ce champ est obligatoire.',
  };
  protected readonly dateMessages = {
    required: 'La date est obligatoire.',
    invalidDate: 'Date invalide.',
    future: 'La date doit être dans le futur.',
  };
  protected readonly seatsMessages = {
    required: 'Le nombre de places est obligatoire.',
    min: 'Au moins 1 place.',
    integer: 'Un nombre entier est attendu.',
  };
  protected readonly keyMessages = {
    required: 'Le nom est obligatoire.',
    pattern: 'Le nom est obligatoire.',
  };

  constructor() {
    merge(toObservable(this.id), this.retries.pipe(map(() => this.id())))
      .pipe(
        tap((id) => {
          if (id) {
            this.state.set('loading');
          }
        }),
        switchMap((id) =>
          id
            ? this.eventService.get(id).pipe(
                map((event) => this.classify(event)),
                catchError((error: unknown) =>
                  of<LoadResult>({
                    event: null,
                    state:
                      error instanceof HttpErrorResponse && error.status === 404
                        ? 'notFound'
                        : 'error',
                  }),
                ),
              )
            : of<LoadResult>({ event: null, state: 'ready' }),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ event, state }) => {
        this.original = event;
        if (event) {
          this.fillForm(event);
        }
        this.state.set(state);
      });
  }

  protected retry(): void {
    this.retries.next();
  }

  protected addAttribute(): void {
    this.form.controls.attributes.push(this.createAttributeRow());
  }

  protected removeAttribute(index: number): void {
    this.form.controls.attributes.removeAt(index);
  }

  protected submit(): void {
    if (this.saving()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const request = this.buildRequest();
    if (!request) {
      return;
    }
    const id = this.id();
    const call$ = id ? this.eventService.update(id, request) : this.eventService.create(request);
    this.saving.set(true);
    call$.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.notifications.success(id ? 'Événement mis à jour.' : 'Événement créé.');
        void this.router.navigate(['/organizer/events']);
      },
      error: (error: HttpErrorResponse) => this.handleError(error, id !== undefined),
    });
  }

  private classify(event: EventResponse): LoadResult {
    // UX only: the backend enforces ownership with a 403.
    return event.organizerId === this.auth.userId()
      ? { event, state: 'ready' }
      : { event: null, state: 'forbidden' };
  }

  private createAttributeRow(row: AttributeRow = { key: '', value: '' }): AttributeGroup {
    return this.fb.group({
      key: [row.key, [Validators.required, Validators.pattern(/\S/)]],
      value: [row.value],
    });
  }

  private fillForm(event: EventResponse): void {
    this.form.reset({
      title: event.title,
      description: event.description ?? '',
      type: event.type,
      city: event.city,
      venue: event.venue,
      startDate: toLocalInput(event.startDate),
      totalSeats: event.totalSeats,
    });
    const attributes = this.form.controls.attributes;
    attributes.clear();
    for (const row of toAttributeRows(event.attributes)) {
      attributes.push(this.createAttributeRow(row));
    }
  }

  private buildRequest(): EventRequest | null {
    const raw = this.form.getRawValue();
    const startDate = toIsoUtc(raw.startDate);
    if (startDate === null || raw.totalSeats === null) {
      return null; // the validators make this unreachable
    }
    return {
      title: raw.title.trim(),
      description: raw.description.trim() || undefined,
      type: raw.type,
      city: raw.city.trim(),
      venue: raw.venue.trim(),
      startDate,
      totalSeats: raw.totalSeats,
      attributes: buildAttributes(raw.attributes, this.original?.attributes),
    };
  }

  private handleError(error: HttpErrorResponse, editing: boolean): void {
    if (error.status === 400 && applyServerErrors(this.form, error)) {
      return;
    }
    if (error.status === 403) {
      this.notifications.error(
        editing
          ? 'Vous ne pouvez modifier que vos propres événements.'
          : 'Seuls les organisateurs peuvent créer des événements.',
      );
      return;
    }
    if (error.status === 404 && editing) {
      this.notifications.error('Cet événement n’existe plus.');
      void this.router.navigate(['/organizer/events']);
      return;
    }
    this.notifications.error(toUserMessage(error.status));
  }
}
