package com.billetterie.ticket_service.dto;

import com.billetterie.ticket_service.ticket.Ticket;
import com.billetterie.ticket_service.ticket.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public record TicketResponse(UUID id, String eventId, TicketStatus status, Instant createdAt) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(ticket.getId(), ticket.getEventId(), ticket.getStatus(), ticket.getCreatedAt());
    }
}