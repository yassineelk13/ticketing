import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { catchError, map, of, Subject, switchMap, tap } from 'rxjs';
import { EVENT_TYPE_LABELS, EventResponse } from '../../../core/models/event.model';
import { EventFilters, EventService } from '../event.service';

type LoadState = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-events-list',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './events-list.html',
  styleUrl: './events-list.scss',
})
export class EventsList {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly eventService = inject(EventService);
  private readonly searches = new Subject<EventFilters>();

  protected readonly form = this.fb.group({ city: '', date: '' });
  protected readonly state = signal<LoadState>('loading');
  protected readonly events = signal<EventResponse[]>([]);
  protected readonly filtered = signal(false);
  protected readonly typeLabels = EVENT_TYPE_LABELS;

  constructor() {
    this.searches
      .pipe(
        tap(() => this.state.set('loading')),
        // switchMap cancels the previous request if a new search starts.
        switchMap((filters) =>
          this.eventService.list(filters).pipe(
            map((events) => ({ events, failed: false })),
            // catchError stays INSIDE switchMap: an error must not kill the search stream.
            catchError(() => of({ events: [] as EventResponse[], failed: true })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ events, failed }) => {
        this.events.set(events);
        this.state.set(failed ? 'error' : 'ready');
      });

    this.searches.next({});
  }

  protected search(): void {
    const filters = this.form.getRawValue();
    this.filtered.set(filters.city.trim() !== '' || filters.date !== '');
    this.searches.next(filters);
  }

  protected reset(): void {
    this.form.reset();
    this.search();
  }
}
