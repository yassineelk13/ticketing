package com.billetterie.notification_service.notification;

import java.util.UUID;

public record Notification(UUID userId, UUID ticketId, String subject, String body) {
}