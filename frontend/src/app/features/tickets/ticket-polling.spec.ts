import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { EventResponse } from '../../core/models/event.model';
import { TicketStatus } from '../../core/models/ticket.model';
import { describeStatusChanges, hasPendingTickets, watchTickets } from './ticket-polling';
import { TicketView } from './ticket-view.service';

const eventOf = (title: string): EventResponse => ({
  id: 'e1',
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

const view = (id: string, status: TicketStatus, title = 'Concert Gnawa'): TicketView => ({
  ticket: { id, eventId: 'e1', status, createdAt: '2026-12-01T10:00:00Z' },
  event: eventOf(title),
});

function collect(source: Observable<TicketView[]>) {
  const result = { emissions: [] as TicketView[][], completed: false, failed: false };
  source.subscribe({
    next: (views) => result.emissions.push(views),
    complete: () => (result.completed = true),
    error: () => (result.failed = true),
  });
  return result;
}

describe('hasPendingTickets', () => {
  it('is true only when a ticket is RESERVED', () => {
    expect(hasPendingTickets([view('t1', 'PAID'), view('t2', 'RESERVED')])).toBe(true);
    expect(hasPendingTickets([view('t1', 'PAID'), view('t2', 'CANCELLED')])).toBe(false);
    expect(hasPendingTickets([])).toBe(false);
  });
});

describe('watchTickets', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('emits once and completes when nothing is pending', () => {
    let calls = 0;
    const result = collect(
      watchTickets(() => {
        calls++;
        return of([view('t1', 'PAID')]);
      }),
    );

    expect(result.emissions).toHaveLength(1);
    expect(result.completed).toBe(true);
    expect(calls).toBe(1);
  });

  it('keeps refreshing while a ticket is reserved, then stops by itself', () => {
    vi.useFakeTimers();
    const statuses: TicketStatus[] = ['RESERVED', 'RESERVED', 'PAID'];
    let calls = 0;
    const result = collect(
      watchTickets(() => {
        const status = statuses[Math.min(calls, statuses.length - 1)] ?? 'PAID';
        calls++;
        return of([view('t1', status)]);
      }, 1000),
    );

    expect(result.emissions).toHaveLength(1);

    vi.advanceTimersByTime(1000);
    expect(result.emissions).toHaveLength(2);
    expect(result.completed).toBe(false);

    vi.advanceTimersByTime(1000);
    expect(result.emissions).toHaveLength(3);
    expect(result.completed).toBe(true);

    vi.advanceTimersByTime(10_000);
    expect(calls).toBe(3);
  });

  it('stops after the maximum duration even if a ticket is still reserved', () => {
    vi.useFakeTimers();
    let calls = 0;
    const result = collect(
      watchTickets(
        () => {
          calls++;
          return of([view('t1', 'RESERVED')]);
        },
        1000,
        3500,
      ),
    );

    vi.advanceTimersByTime(10_000);

    expect(result.completed).toBe(true);
    expect(calls).toBe(4); // at 0 s, 1 s, 2 s and 3 s; a 5th call would end after the 3.5 s limit
  });

  it('keeps the last data and tries again when a refresh fails', () => {
    vi.useFakeTimers();
    let calls = 0;
    const result = collect(
      watchTickets(() => {
        calls++;
        if (calls === 2) {
          return throwError(() => new Error('network'));
        }
        return of([view('t1', calls === 1 ? 'RESERVED' : 'PAID')]);
      }, 1000),
    );

    vi.advanceTimersByTime(1000); // the refresh fails
    vi.advanceTimersByTime(1000); // the next one works

    expect(result.failed).toBe(false);
    expect(result.emissions.map((views) => views[0]?.ticket.status)).toEqual([
      'RESERVED',
      'RESERVED',
      'PAID',
    ]);
    expect(result.completed).toBe(true);
  });

  it('fails when the first load fails', () => {
    const result = collect(watchTickets(() => throwError(() => new Error('boom'))));

    expect(result.failed).toBe(true);
    expect(result.emissions).toEqual([]);
  });
});

describe('describeStatusChanges', () => {
  it('returns null when nothing changed', () => {
    const views = [view('t1', 'RESERVED'), view('t2', 'PAID')];
    expect(describeStatusChanges(views, views)).toBeNull();
  });

  it('announces a ticket that left RESERVED, with the event title and the new status', () => {
    const message = describeStatusChanges(
      [view('t1', 'RESERVED')],
      [view('t1', 'CANCELLED', 'Rap marocaine')],
    );
    expect(message).toBe('Votre billet pour Rap marocaine est maintenant : Annulé.');
  });

  it('ignores tickets that were not in the previous list', () => {
    expect(describeStatusChanges([], [view('t1', 'PAID')])).toBeNull();
  });

  it('joins several changes', () => {
    const message = describeStatusChanges(
      [view('t1', 'RESERVED'), view('t2', 'RESERVED')],
      [view('t1', 'PAID'), view('t2', 'CANCELLED')],
    );
    expect(message).toContain('Payé');
    expect(message).toContain('Annulé');
  });
});
