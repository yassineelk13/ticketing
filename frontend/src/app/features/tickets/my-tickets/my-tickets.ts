import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { catchError, map, of, Subject, switchMap, tap } from 'rxjs';
import { TICKET_STATUS_LABELS } from '../../../core/models/ticket.model';
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
  protected readonly statusLabels = TICKET_STATUS_LABELS;

  constructor() {
    this.reloads
      .pipe(
        tap(() => this.state.set('loading')),
        switchMap(() =>
          this.ticketViews.load().pipe(
            map((views) => ({ views, failed: false })),
            catchError(() => of({ views: [] as TicketView[], failed: true })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ views, failed }) => {
        this.views.set(views);
        this.state.set(failed ? 'error' : 'ready');
      });

    this.reloads.next();
  }

  protected reload(): void {
    this.reloads.next();
  }
}
