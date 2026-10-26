package com.billetterie.notification_service.messaging;

import java.util.UUID;

public record PaymentCompletedEvent(UUID ticketId, String eventId, UUID userId, UUID paymentId) {
}