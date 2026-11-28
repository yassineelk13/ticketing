import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { catchError, map, of, Subject, switchMap, tap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { EVENT_TYPE_LABELS, EventResponse } from '../../../core/models/event.model';
import { EventService } from '../../events/event.service';
import { ownEvents } from '../own-events';

type LoadState = 'loading' | 'ready' | 'error';

interface LoadResult {
  events: EventResponse[];
  failed: boolean;
}

@Component({
  selector: 'app-my-events',
  imports: [DatePipe, RouterLink, MatButtonModule, MatCardModule, MatProgressSpinnerModule],
  templateUrl: './my-events.html',
  styleUrl: './my-events.scss',
})
export class MyEvents {
  private readonly eventService = inject(EventService);
  private readonly auth = inject(AuthService);
  private readonly reloads = new Subject<void>();

  protected readonly state = signal<LoadState>('loading');
  protected readonly events = signal<EventResponse[]>([]);
  protected readonly typeLabels = EVENT_TYPE_LABELS;

  constructor() {
    this.reloads
      .pipe(
        tap(() => this.state.set('loading')),
        switchMap(() =>
          this.eventService.list().pipe(
            map((events): LoadResult => ({ events, failed: false })),
            catchError(() => of<LoadResult>({ events: [], failed: true })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ events, failed }) => {
        this.events.set(ownEvents(events, this.auth.userId()));
        this.state.set(failed ? 'error' : 'ready');
      });

    this.reloads.next();
  }

  protected reload(): void {
    this.reloads.next();
  }
}
