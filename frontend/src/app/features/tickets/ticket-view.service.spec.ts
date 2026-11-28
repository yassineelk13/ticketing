import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EventResponse } from '../../core/models/event.model';
import { TicketResponse } from '../../core/models/ticket.model';
import { TicketView, TicketViewService } from './ticket-view.service';

const ticket = (id: string, eventId: string, createdAt: string): TicketResponse => ({
  id,
  eventId,
  status: 'RESERVED',
  createdAt,
});

const event = (id: string, title: string): EventResponse => ({
  id,
  title,
  description: null,
  type: 'CONCERT',
  city: 'Agadir',
  venue: 'Theatre de Verdure',
  startDate: '2026-12-20T20:00:00Z',
  totalSeats: 200,
  organizerId: 'o1',
  attributes: {},
});

describe('TicketViewService', () => {
  let service: TicketViewService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TicketViewService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('returns an empty list without requesting any event', () => {
    let result: TicketView[] | undefined;
    service.load().subscribe((views) => (result = views));

    httpMock.expectOne('/api/tickets/my-tickets').flush([]);

    expect(result).toEqual([]);
    httpMock.expectNone((req) => req.url.startsWith('/api/events'));
  });

  it('loads each distinct event once and sorts the newest ticket first', () => {
    let result: TicketView[] | undefined;
    service.load().subscribe((views) => (result = views));

    httpMock
      .expectOne('/api/tickets/my-tickets')
      .flush([
        ticket('t1', 'e1', '2026-12-01T10:00:00Z'),
        ticket('t2', 'e2', '2026-12-01T12:00:00Z'),
        ticket('t3', 'e1', '2026-12-01T11:00:00Z'),
      ]);
    // expectOne fails if e1 were requested twice: this proves the deduplication.
    httpMock.expectOne('/api/events/e1').flush(event('e1', 'Concert Gnawa'));
    httpMock.expectOne('/api/events/e2').flush(event('e2', 'Rap marocaine'));

    expect(result?.map((view) => view.ticket.id)).toEqual(['t2', 't3', 't1']);
    expect(result?.map((view) => view.event?.title)).toEqual([
      'Rap marocaine',
      'Concert Gnawa',
      'Concert Gnawa',
    ]);
  });

  it('keeps the ticket with a null event when its event cannot be loaded', () => {
    let result: TicketView[] | undefined;
    service.load().subscribe((views) => (result = views));

    httpMock
      .expectOne('/api/tickets/my-tickets')
      .flush([ticket('t1', 'e1', '2026-12-01T10:00:00Z')]);
    httpMock.expectOne('/api/events/e1').flush(null, { status: 404, statusText: 'Not Found' });

    expect(result).toEqual([{ ticket: ticket('t1', 'e1', '2026-12-01T10:00:00Z'), event: null }]);
  });

  it('fails when the tickets request fails', () => {
    let failed = false;
    service.load().subscribe({ error: () => (failed = true) });

    httpMock
      .expectOne('/api/tickets/my-tickets')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });

    expect(failed).toBe(true);
  });
});
