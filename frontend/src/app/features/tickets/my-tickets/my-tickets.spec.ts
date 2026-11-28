import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EventResponse } from '../../../core/models/event.model';
import { TicketResponse, TicketStatus } from '../../../core/models/ticket.model';
import { MyTickets } from './my-tickets';
import { vi } from 'vitest';
import { POLL_INTERVAL_MS } from '../ticket-polling';

const ticket = (id: string, status: TicketStatus): TicketResponse => ({
  id: `${id}-0000-0000-0000-000000000000`,
  eventId: 'e1',
  status,
  createdAt: '2026-12-01T10:00:00Z',
});

const gnawa: EventResponse = {
  id: 'e1',
  title: 'Concert Gnawa',
  description: null,
  type: 'CONCERT',
  city: 'Agadir',
  venue: 'Theatre de Verdure',
  startDate: '2026-12-20T20:00:00Z',
  totalSeats: 200,
  organizerId: 'o1',
  attributes: {},
};

describe('MyTickets', () => {
  let httpMock: HttpTestingController;
  let fixture: ComponentFixture<MyTickets>;

  const root = (): HTMLElement => fixture.nativeElement;
  const text = (): string => root().textContent ?? '';

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MyTickets],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MyTickets);
  });

  afterEach(() => httpMock.verify());

  it('shows a loading state, then the tickets with their event title and status', async () => {
    fixture.detectChanges();
    expect(text()).toContain('Chargement');

    httpMock
      .expectOne('/api/tickets/my-tickets')
      .flush([ticket('aaaaaaaa', 'PAID'), ticket('bbbbbbbb', 'CANCELLED')]);
    httpMock.expectOne('/api/events/e1').flush(gnawa);
    await settle();

    expect(root().querySelectorAll('.ticket-card').length).toBe(2);
    expect(text()).toContain('Concert Gnawa');
    expect(text()).toContain('Payé');
    expect(text()).toContain('Annulé');
    expect(text()).not.toContain('Chargement');
  });

  it('shows an empty state with a link to the events', async () => {
    httpMock.expectOne('/api/tickets/my-tickets').flush([]);
    await settle();

    expect(text()).toContain('Vous n’avez pas encore de billet.');
    expect(root().querySelector('a[href="/events"]')).not.toBeNull();
  });

  it('shows an error state and recovers with the retry button', async () => {
    httpMock
      .expectOne('/api/tickets/my-tickets')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    await settle();
    expect(root().querySelector('[role="alert"]')?.textContent).toContain('Impossible');

    root().querySelector<HTMLButtonElement>('button.retry')?.click();
    httpMock.expectOne('/api/tickets/my-tickets').flush([ticket('aaaaaaaa', 'RESERVED')]);
    httpMock.expectOne('/api/events/e1').flush(gnawa);
    await settle();

    expect(text()).toContain('En attente de paiement');
    expect(root().querySelector('[role="alert"]')).toBeNull();
  });

  it('keeps the ticket visible when its event cannot be loaded', async () => {
    httpMock.expectOne('/api/tickets/my-tickets').flush([ticket('aaaaaaaa', 'PAID')]);
    httpMock.expectOne('/api/events/e1').flush(null, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(text()).toContain('Événement indisponible');
    expect(text()).toContain('Payé');
  });

  describe('live status', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('refreshes while a ticket is reserved, announces the change, then stops', () => {
      fixture.detectChanges();
      httpMock.expectOne('/api/tickets/my-tickets').flush([ticket('aaaaaaaa', 'RESERVED')]);
      httpMock.expectOne('/api/events/e1').flush(gnawa);
      fixture.detectChanges();
      expect(text()).toContain('En attente de paiement');
      expect(text()).toContain('Mise à jour automatique');

      vi.advanceTimersByTime(POLL_INTERVAL_MS);
      httpMock.expectOne('/api/tickets/my-tickets').flush([ticket('aaaaaaaa', 'PAID')]);
      httpMock.expectNone('/api/events/e1'); // the event is cached: only the tickets are refreshed
      fixture.detectChanges();

      expect(text()).toContain('Payé');
      expect(root().querySelector('.announcement')?.textContent).toContain('Concert Gnawa');
      expect(text()).not.toContain('Mise à jour automatique');

      vi.advanceTimersByTime(POLL_INTERVAL_MS * 5);
      httpMock.expectNone('/api/tickets/my-tickets');
    });
  });
});
