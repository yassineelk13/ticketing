export type EventType = 'CONCERT' | 'CONFERENCE' | 'SPORT' | 'OTHER';

export interface EventResponse {
  id: string;
  title: string;
  description: string | null;
  type: EventType;
  city: string;
  venue: string;
  startDate: string; // ISO 8601 UTC
  totalSeats: number;
  organizerId: string;
  attributes: Record<string, unknown> | null;
}

export interface EventRequest {
  title: string;
  description?: string;
  type: EventType;
  city: string;
  venue: string;
  startDate: string; // ISO 8601 UTC, must be in the future
  totalSeats: number;
  attributes?: Record<string, unknown>;
}
