import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { catchError, finalize, map, of, Subject, switchMap, tap } from 'rxjs';
import { toUserMessage } from '../../../core/http/problem';
import { EventResponse } from '../../../core/models/event.model';
import { TICKET_STATUS_LABELS } from '../../../core/models/ticket.model';
import { NotificationService } from '../../../core/notification/notification.service';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/confirm-dialog/confirm-dialog';
import { describeStatusChanges, hasPendingTickets, watchTickets } from '../ticket-polling';
import { TicketService } from '../ticket.service';
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
  private readonly tickets = inject(TicketService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);
  private readonly reloads = new Subject<void>();

  protected readonly state = signal<LoadState>('loading');
  protected readonly views = signal<TicketView[]>([]);
  protected readonly polling = signal(false);
  protected readonly announcement = signal('');
  protected readonly cancellingId = signal<string | null>(null);
  protected readonly hasPending = computed(() => hasPendingTickets(this.views()));
  protected readonly statusLabels = TICKET_STATUS_LABELS;

  constructor() {
    this.reloads
      .pipe(
        tap(() => {
          this.state.set('loading');
          this.announcement.set('');
        }),
        // switchMap also cancels a running polling loop when the list is reloaded.
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

  protected cancel(view: TicketView): void {
    if (this.cancellingId() !== null) {
      return;
    }
    const title = view.event?.title ?? 'cet événement';
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, {
        data: {
          title: 'Annuler ce billet ?',
          message: `Votre billet pour « ${title} » sera annulé et la place sera libérée. Cette action est définitive.`,
          confirmLabel: 'Annuler le billet',
          cancelLabel: 'Garder le billet',
        },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed === true) {
          this.confirmCancel(view);
        }
      });
  }

  private confirmCancel(view: TicketView): void {
    this.cancellingId.set(view.ticket.id);
    this.tickets
      .cancel(view.ticket.id)
      .pipe(finalize(() => this.cancellingId.set(null)))
      .subscribe({
        next: () => {
          this.notifications.success('Billet annulé.');
          this.reload(); // show what the server says, whether it deleted or cancelled the ticket
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 404) {
            this.notifications.error('Ce billet n’existe plus ou ne vous appartient pas.');
            this.reload(); // the list was out of date
          } else {
            this.notifications.error(toUserMessage(error.status));
          }
        },
      });
  }
}
