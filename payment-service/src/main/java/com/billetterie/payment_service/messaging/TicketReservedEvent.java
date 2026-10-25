package com.billetterie.payment_service.messaging;

import java.util.UUID;

public record TicketReservedEvent(UUID ticketId, String eventId, UUID userId) {
}