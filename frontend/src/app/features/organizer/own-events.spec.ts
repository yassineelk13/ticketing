import { EventResponse } from '../../core/models/event.model';
import { ownEvents } from './own-events';

const event = (id: string, organizerId: string, startDate: string): EventResponse => ({
  id,
  title: `Event ${id}`,
  description: null,
  type: 'CONCERT',
  city: 'Agadir',
  venue: 'Theatre de Verdure',
  startDate,
  totalSeats: 100,
  organizerId,
  attributes: {},
});

describe('ownEvents', () => {
  it('keeps only the events of the organizer, soonest first', () => {
    const events = [
      event('1', 'o1', '2026-12-20T20:00:00Z'),
      event('2', 'o2', '2026-12-01T20:00:00Z'),
      event('3', 'o1', '2026-12-10T20:00:00Z'),
    ];

    expect(ownEvents(events, 'o1').map((e) => e.id)).toEqual(['3', '1']);
  });

  it('does not modify the list it receives', () => {
    const events = [
      event('1', 'o1', '2026-12-20T20:00:00Z'),
      event('3', 'o1', '2026-12-10T20:00:00Z'),
    ];

    ownEvents(events, 'o1');

    expect(events.map((e) => e.id)).toEqual(['1', '3']);
  });

  it('returns nothing without an organizer id', () => {
    expect(ownEvents([event('1', 'o1', '2026-12-20T20:00:00Z')], null)).toEqual([]);
  });
});
