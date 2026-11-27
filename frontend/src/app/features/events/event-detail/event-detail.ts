import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { catchError, map, merge, of, Subject, switchMap, tap } from 'rxjs';
import { EVENT_TYPE_LABELS, EventResponse } from '../../../core/models/event.model';
import { attributeEntries } from '../event-attributes';
import { EventService } from '../event.service';
import { ReserveButton } from '../../tickets/reserve-button/reserve-button';

type LoadState = 'loading' | 'ready' | 'notFound' | 'error';

interface LoadResult {
  event: EventResponse | null;
  state: LoadState;
}

@Component({
  selector: 'app-event-detail',
  imports: [DatePipe, RouterLink, MatButtonModule, MatProgressSpinnerModule, ReserveButton],
  templateUrl: './event-detail.html',
  styleUrl: './event-detail.scss',
})
export class EventDetail {
  /** Bound from the :id route parameter (withComponentInputBinding). */
  readonly id = input.required<string>();

  private readonly eventService = inject(EventService);
  private readonly pageTitle = inject(Title);
  private readonly reloads = new Subject<void>();

  protected readonly state = signal<LoadState>('loading');
  protected readonly event = signal<EventResponse | null>(null);
  protected readonly attributes = computed(() => attributeEntries(this.event()?.attributes));
  protected readonly typeLabels = EVENT_TYPE_LABELS;

  constructor() {
    merge(toObservable(this.id), this.reloads.pipe(map(() => this.id())))
      .pipe(
        tap(() => this.state.set('loading')),
        switchMap((id) =>
          this.eventService.get(id).pipe(
            map((event): LoadResult => ({ event, state: 'ready' })),
            catchError((error: unknown) =>
              of<LoadResult>({
                event: null,
                state:
                  error instanceof HttpErrorResponse && error.status === 404 ? 'notFound' : 'error',
              }),
            ),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ event, state }) => {
        this.event.set(event);
        this.state.set(state);
        if (event) {
          this.pageTitle.setTitle(`${event.title} - Billetterie`);
        }
      });
  }

  protected retry(): void {
    this.reloads.next();
  }
}
