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

export const TICKET_STATUS_LABELS: Record<TicketStatus, string> = {
  RESERVED: 'En attente de paiement',
  PAID: 'Payé',
  CANCELLED: 'Annulé',
};
