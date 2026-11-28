import { inject, Service } from '@angular/core';
import { catchError, forkJoin, map, Observable, of, switchMap, tap } from 'rxjs';
import { EventResponse } from '../../core/models/event.model';
import { TicketResponse } from '../../core/models/ticket.model';
import { EventService } from '../events/event.service';
import { TicketService } from './ticket.service';

export interface TicketView {
  ticket: TicketResponse;
  event: EventResponse | null; // null when the event cannot be loaded
}

@Service()
export class TicketViewService {
  private readonly tickets = inject(TicketService);
  private readonly events = inject(EventService);

  /**
   * Loads my tickets (newest first) with their event: the ticket API does not return the title.
   * Events already in `eventCache` are not requested again, so a polling loop that reuses the same
   * cache only refreshes the tickets. Failed events are not cached and will be retried.
   */
  load(eventCache = new Map<string, EventResponse>()): Observable<TicketView[]> {
    return this.tickets.myTickets().pipe(
      switchMap((tickets) => {
        const sorted = [...tickets].sort(
          (a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt),
        );
        const missing = [...new Set(sorted.map((ticket) => ticket.eventId))].filter(
          (id) => !eventCache.has(id),
        );
        // forkJoin([]) completes without emitting, so skip it when everything is cached.
        const loaded$: Observable<unknown> =
          missing.length === 0
            ? of(null)
            : forkJoin(
                missing.map((id) =>
                  this.events.get(id).pipe(
                    tap((event) => eventCache.set(id, event)),
                    catchError(() => of(null)),
                  ),
                ),
              );
        return loaded$.pipe(
          map((): TicketView[] =>
            sorted.map((ticket) => ({ ticket, event: eventCache.get(ticket.eventId) ?? null })),
          ),
        );
      }),
    );
  }
}
