package com.billetterie.ticket_service.dto;

import jakarta.validation.constraints.NotBlank;

public record ReserveRequest(@NotBlank String eventId) {
}