package com.billetterie.payment_service.messaging;

import java.util.UUID;

public record PaymentFailedEvent(UUID ticketId, String eventId, UUID userId, String reason) {
}