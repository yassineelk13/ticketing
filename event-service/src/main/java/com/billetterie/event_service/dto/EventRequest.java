package com.billetterie.event_service.dto;

import com.billetterie.event_service.event.EventType;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.Map;

public record EventRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 2000) String description,
        @NotNull EventType type,
        @NotBlank String city,
        @NotBlank String venue,
        @NotNull @Future Instant startDate,
        @Min(1) int totalSeats,
        Map<String, Object> attributes
) {
}