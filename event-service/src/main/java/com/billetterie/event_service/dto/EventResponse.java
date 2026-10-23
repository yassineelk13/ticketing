package com.billetterie.event_service.dto;

import com.billetterie.event_service.event.Event;
import com.billetterie.event_service.event.EventType;

import java.time.Instant;
import java.util.Map;

public record EventResponse(
        String id, String title, String description, EventType type, String city, String venue,
        Instant startDate, int totalSeats, String organizerId, Map<String, Object> attributes
) {
    public static EventResponse from(Event e) {
        return new EventResponse(e.getId(), e.getTitle(), e.getDescription(), e.getType(),
                e.getCity(), e.getVenue(), e.getStartDate(), e.getTotalSeats(),
                e.getOrganizerId(), e.getAttributes());
    }
}