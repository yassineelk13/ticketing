import { inject, Service } from '@angular/core';
import { catchError, forkJoin, map, Observable, of, switchMap } from 'rxjs';
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

  /** Loads my tickets (newest first) with their event: the ticket API does not return the title. */
  load(): Observable<TicketView[]> {
    return this.tickets.myTickets().pipe(
      switchMap((tickets) => {
        const sorted = [...tickets].sort(
          (a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt),
        );
        const ids = [...new Set(sorted.map((ticket) => ticket.eventId))];
        if (ids.length === 0) {
          // forkJoin([]) completes without emitting, so handle "no ticket" here.
          return of<TicketView[]>([]);
        }
        return forkJoin(ids.map((id) => this.events.get(id).pipe(catchError(() => of(null))))).pipe(
          map((events) => {
            const byId = new Map<string, EventResponse | null>(
              ids.map((id, index): [string, EventResponse | null] => [id, events[index] ?? null]),
            );
            return sorted.map((ticket) => ({ ticket, event: byId.get(ticket.eventId) ?? null }));
          }),
        );
      }),
    );
  }
}
