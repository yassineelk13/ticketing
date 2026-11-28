import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TicketService } from './ticket.service';

describe('TicketService', () => {
  let service: TicketService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TicketService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('posts the event id to reserve a place', () => {
    service.reserve('e1').subscribe();
    const req = httpMock.expectOne('/api/tickets/reserve');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ eventId: 'e1' });
    req.flush(
      { id: 't1', eventId: 'e1', status: 'RESERVED', createdAt: '2026-12-01T10:00:00Z' },
      { status: 201, statusText: 'Created' },
    );
  });

  it('fetches my tickets', () => {
    service.myTickets().subscribe();
    const req = httpMock.expectOne('/api/tickets/my-tickets');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
