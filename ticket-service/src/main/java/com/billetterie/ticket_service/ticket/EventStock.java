package com.billetterie.ticket_service.ticket;

import jakarta.persistence.*;


@Entity
@Table(name = "event_stocks")
public class EventStock {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    @Version
    private long version;

    protected EventStock() {
        // requis par JPA
    }

    public EventStock(String eventId, int totalSeats) {
        this.eventId = eventId;
        this.totalSeats = totalSeats;
        this.availableSeats = totalSeats;
    }

    public boolean hasAvailableSeats() {
        return availableSeats > 0;
    }

    public void reserveSeat() {
        if (availableSeats <= 0) {
            throw new IllegalStateException("No seats available for event " + eventId);
        }
        availableSeats--;
    }

    public void releaseSeat() {
        if (availableSeats < totalSeats) {
            availableSeats++;
        }
    }

    public String getEventId() { return eventId; }
    public int getTotalSeats() { return totalSeats; }
    public int getAvailableSeats() { return availableSeats; }
}