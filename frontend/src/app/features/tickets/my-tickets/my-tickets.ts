import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { catchError, finalize, map, of, Subject, switchMap, tap } from 'rxjs';
import { EventResponse } from '../../../core/models/event.model';
import { TICKET_STATUS_LABELS } from '../../../core/models/ticket.model';
import { describeStatusChanges, hasPendingTickets, watchTickets } from '../ticket-polling';
import { TicketView, TicketViewService } from '../ticket-view.service';

type LoadState = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-my-tickets',
  imports: [DatePipe, RouterLink, MatButtonModule, MatCardModule, MatProgressSpinnerModule],
  templateUrl: './my-tickets.html',
  styleUrl: './my-tickets.scss',
})
export class MyTickets {
  private readonly ticketViews = inject(TicketViewService);
  private readonly reloads = new Subject<void>();

  protected readonly state = signal<LoadState>('loading');
  protected readonly views = signal<TicketView[]>([]);
  protected readonly polling = signal(false);
  protected readonly announcement = signal('');
  protected readonly hasPending = computed(() => hasPendingTickets(this.views()));
  protected readonly statusLabels = TICKET_STATUS_LABELS;

  constructor() {
    this.reloads
      .pipe(
        tap(() => {
          this.state.set('loading');
          this.announcement.set('');
        }),
        // switchMap also cancels a running polling loop when the user reloads manually.
        switchMap(() => {
          const eventCache = new Map<string, EventResponse>();
          return watchTickets(() => this.ticketViews.load(eventCache)).pipe(
            map((views) => ({ views, failed: false })),
            catchError(() => of({ views: [] as TicketView[], failed: true })),
            finalize(() => this.polling.set(false)),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe(({ views, failed }) => {
        const change = failed ? null : describeStatusChanges(this.views(), views);
        if (change) {
          this.announcement.set(change);
        }
        this.views.set(views);
        this.state.set(failed ? 'error' : 'ready');
        this.polling.set(!failed && hasPendingTickets(views));
      });

    this.reloads.next();
  }

  protected reload(): void {
    this.reloads.next();
  }
}
