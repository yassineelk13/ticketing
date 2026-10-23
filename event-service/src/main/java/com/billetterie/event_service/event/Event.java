package com.billetterie.event_service.event;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "events")
public class Event {

    @Id
    private String id;

    private String title;
    private String description;
    private EventType type;

    @Indexed
    private String city;
    private String venue;

    @Indexed
    private Instant startDate;

    private int totalSeats;

    @Indexed
    private String organizerId;

    private Map<String, Object> attributes = new HashMap<>();
    private Instant createdAt;

    protected Event() {
    }

    public Event(String title, String description, EventType type, String city, String venue,
                 Instant startDate, int totalSeats, String organizerId, Map<String, Object> attributes) {
        this.title = title;
        this.description = description;
        this.type = type;
        this.city = city;
        this.venue = venue;
        this.startDate = startDate;
        this.totalSeats = totalSeats;
        this.organizerId = organizerId;
        this.attributes = attributes != null ? attributes : new HashMap<>();
        this.createdAt = Instant.now();
    }

    public void update(String title, String description, EventType type, String city, String venue,
                       Instant startDate, int totalSeats, Map<String, Object> attributes) {
        this.title = title;
        this.description = description;
        this.type = type;
        this.city = city;
        this.venue = venue;
        this.startDate = startDate;
        this.totalSeats = totalSeats;
        this.attributes = attributes != null ? attributes : new HashMap<>();
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public EventType getType() { return type; }
    public String getCity() { return city; }
    public String getVenue() { return venue; }
    public Instant getStartDate() { return startDate; }
    public int getTotalSeats() { return totalSeats; }
    public String getOrganizerId() { return organizerId; }
    public Map<String, Object> getAttributes() { return attributes; }
    public Instant getCreatedAt() { return createdAt; }
}