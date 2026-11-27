import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../../core/api.config';
import { SILENT_ERRORS } from '../../core/http/api-context';
import { ReserveRequest, TicketResponse } from '../../core/models/ticket.model';

@Service()
export class TicketService {
  private readonly http = inject(HttpClient);
  // Callers show their own, more specific messages (409 full, 404 unknown event...).
  private readonly silentErrors = new HttpContext().set(SILENT_ERRORS, true);

  reserve(eventId: string): Observable<TicketResponse> {
    const body: ReserveRequest = { eventId };
    return this.http.post<TicketResponse>(`${API_BASE}/tickets/reserve`, body, {
      context: this.silentErrors,
    });
  }
}
