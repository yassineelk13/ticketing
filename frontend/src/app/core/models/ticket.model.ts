export type TicketStatus = 'RESERVED' | 'PAID' | 'CANCELLED';

export interface TicketResponse {
  id: string;
  eventId: string;
  status: TicketStatus;
  createdAt: string; // ISO 8601 UTC
}

export interface ReserveRequest {
  eventId: string;
}