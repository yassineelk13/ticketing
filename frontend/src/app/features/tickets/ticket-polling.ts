import { catchError, defer, EMPTY, expand, Observable, of, switchMap, timer } from 'rxjs';
import { TICKET_STATUS_LABELS, TicketStatus } from '../../core/models/ticket.model';
import { TicketView } from './ticket-view.service';

export const POLL_INTERVAL_MS = 3000;
export const POLL_MAX_DURATION_MS = 2 * 60 * 1000;

export function hasPendingTickets(views: TicketView[]): boolean {
  return views.some((view) => view.ticket.status === 'RESERVED');
}

/**
 * Emits the tickets once, then keeps refreshing them while at least one is RESERVED.
 * It stops by itself when nothing is pending, or after maxDurationMs.
 * A failed refresh keeps the last known data and tries again: only the first load can fail the stream.
 */
export function watchTickets(
  load: () => Observable<TicketView[]>,
  intervalMs: number = POLL_INTERVAL_MS,
  maxDurationMs: number = POLL_MAX_DURATION_MS,
): Observable<TicketView[]> {
  return defer(() => {
    const deadline = Date.now() + maxDurationMs;
    return load().pipe(
      expand((views) =>
        hasPendingTickets(views) && Date.now() + intervalMs <= deadline
          ? timer(intervalMs).pipe(switchMap(() => load().pipe(catchError(() => of(views)))))
          : EMPTY,
      ),
    );
  });
}

/** Describes the tickets that just left RESERVED (for a screen-reader announcement). */
export function describeStatusChanges(previous: TicketView[], next: TicketView[]): string | null {
  const previousStatus = new Map(
    previous.map((view): [string, TicketStatus] => [view.ticket.id, view.ticket.status]),
  );
  const messages = next
    .filter(
      (view) =>
        previousStatus.get(view.ticket.id) === 'RESERVED' && view.ticket.status !== 'RESERVED',
    )
    .map(
      (view) =>
        `Votre billet pour ${view.event?.title ?? 'un événement'} est maintenant : ${TICKET_STATUS_LABELS[view.ticket.status]}.`,
    );
  return messages.length > 0 ? messages.join(' ') : null;
}
