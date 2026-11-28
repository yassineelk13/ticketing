import { EventResponse } from '../../core/models/event.model';

/** The API has no "my events" endpoint: keep the events created by this organizer, soonest first. */
export function ownEvents(events: EventResponse[], organizerId: string | null): EventResponse[] {
  if (!organizerId) {
    return [];
  }
  return events
    .filter((event) => event.organizerId === organizerId)
    .sort((a, b) => Date.parse(a.startDate) - Date.parse(b.startDate));
}
