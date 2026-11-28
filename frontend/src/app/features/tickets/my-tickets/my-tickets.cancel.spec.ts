import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { EventResponse } from '../../../core/models/event.model';
import { TicketResponse, TicketStatus } from '../../../core/models/ticket.model';
import { toUserMessage } from '../../../core/http/problem';
import { NotificationService } from '../../../core/notification/notification.service';
import { MyTickets } from './my-tickets';

const ticket = (prefix: string, status: TicketStatus): TicketResponse => ({
  id: `${prefix}-0000-0000-0000-000000000000`,
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

const PAID = ticket('aaaaaaaa', 'PAID');
const CANCELLED = ticket('bbbbbbbb', 'CANCELLED');

describe('MyTickets cancellation', () => {
  let httpMock: HttpTestingController;
  let fixture: ComponentFixture<MyTickets>;
  let confirmed: boolean;
  let dialogsOpened: number;
  let successes: string[];
  let errors: string[];

  const root = (): HTMLElement => fixture.nativeElement;

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function showList(tickets: TicketResponse[]): Promise<void> {
    fixture.detectChanges();
    httpMock.expectOne('/api/tickets/my-tickets').flush(tickets);
    httpMock.expectOne('/api/events/e1').flush(gnawa);
    await settle();
  }

  function clickCancel(): void {
    root().querySelector<HTMLButtonElement>('button.cancel')?.click();
  }

  beforeEach(() => {
    confirmed = true;
    dialogsOpened = 0;
    successes = [];
    errors = [];
    TestBed.configureTestingModule({
      imports: [MyTickets],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MatDialog,
          useValue: {
            open: () => {
              dialogsOpened++;
              return { afterClosed: () => of(confirmed) };
            },
          },
        },
        {
          provide: NotificationService,
          useValue: {
            success: (message: string) => successes.push(message),
            error: (message: string) => errors.push(message),
          },
        },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MyTickets);
  });

  afterEach(() => httpMock.verify());

  it('offers the cancel button only on tickets that are not already cancelled', async () => {
    await showList([PAID, CANCELLED]);

    expect(root().querySelectorAll('.ticket-card').length).toBe(2);
    expect(root().querySelectorAll('button.cancel').length).toBe(1);
    expect(root().querySelector('button.cancel')?.getAttribute('aria-label')).toBe(
      'Annuler le billet pour Concert Gnawa',
    );
  });

  it('asks for confirmation and sends nothing when the user keeps the ticket', async () => {
    confirmed = false;
    await showList([PAID]);

    clickCancel();

    expect(dialogsOpened).toBe(1);
    httpMock.expectNone('/api/tickets/aaaaaaaa-0000-0000-0000-000000000000');
    expect(successes).toEqual([]);
  });

  it('cancels the ticket after confirmation and reloads the list', async () => {
    await showList([PAID]);

    clickCancel();
    const req = httpMock.expectOne({
      method: 'DELETE',
      url: '/api/tickets/aaaaaaaa-0000-0000-0000-000000000000',
    });
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(successes).toEqual(['Billet annulé.']);
    httpMock.expectOne('/api/tickets/my-tickets').flush([]);
    await settle();
    expect(root().textContent).toContain('Vous n’avez pas encore de billet.');
  });

  it('explains a 404 and reloads the outdated list', async () => {
    await showList([PAID]);

    clickCancel();
    httpMock
      .expectOne('/api/tickets/aaaaaaaa-0000-0000-0000-000000000000')
      .flush(null, { status: 404, statusText: 'Not Found' });

    expect(errors).toEqual(['Ce billet n’existe plus ou ne vous appartient pas.']);
    httpMock.expectOne('/api/tickets/my-tickets').flush([]);
    await settle();
  });

  it('shows a common message on a server failure and keeps the ticket', async () => {
    await showList([PAID]);

    clickCancel();
    httpMock
      .expectOne('/api/tickets/aaaaaaaa-0000-0000-0000-000000000000')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    await settle();

    expect(errors).toEqual([toUserMessage(503)]);
    httpMock.expectNone('/api/tickets/my-tickets');
    expect(root().querySelectorAll('.ticket-card').length).toBe(1);
    expect(root().querySelector<HTMLButtonElement>('button.cancel')?.disabled).toBe(false);
  });
});
