import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../../core/api.config';
import { SILENT_ERRORS } from '../../core/http/api-context';
import { EventRequest, EventResponse } from '../../core/models/event.model';

export interface EventFilters {
  city?: string;
  date?: string; // YYYY-MM-DD
}

@Service()
export class EventService {
  private readonly http = inject(HttpClient);
  // The list page shows its own error state with a retry button.
  private readonly silentErrors = new HttpContext().set(SILENT_ERRORS, true);

  list(filters: EventFilters = {}): Observable<EventResponse[]> {
    let params = new HttpParams();
    const city = filters.city?.trim();
    if (city) {
      params = params.set('city', city);
    }
    if (filters.date) {
      params = params.set('date', filters.date);
    }
    return this.http.get<EventResponse[]>(`${API_BASE}/events`, {
      params,
      context: this.silentErrors,
    });
  }
  get(id: string): Observable<EventResponse> {
    return this.http.get<EventResponse>(`${API_BASE}/events/${encodeURIComponent(id)}`, {
      context: this.silentErrors,
    });
  }

  create(request: EventRequest): Observable<EventResponse> {
    return this.http.post<EventResponse>(`${API_BASE}/events`, request, {
      context: this.silentErrors,
    });
  }

  update(id: string, request: EventRequest): Observable<EventResponse> {
    return this.http.put<EventResponse>(`${API_BASE}/events/${encodeURIComponent(id)}`, request, {
      context: this.silentErrors,
    });
  }
}
