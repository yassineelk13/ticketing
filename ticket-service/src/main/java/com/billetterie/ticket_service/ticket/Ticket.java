package com.billetterie.ticket_service.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_tickets_user", columnList = "user_id"),
        @Index(name = "idx_tickets_event", columnList = "event_id")
})
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Ticket() {
        // requis par JPA
    }

    public Ticket(String eventId, UUID userId) {
        this.eventId = eventId;
        this.userId = userId;
        this.status = TicketStatus.RESERVED;
        this.createdAt = Instant.now();
    }

    public void cancel() {
        this.status = TicketStatus.CANCELLED;
    }

    public boolean isCancelled() {
        return status == TicketStatus.CANCELLED;
    }

    public UUID getId() { return id; }
    public String getEventId() { return eventId; }
    public UUID getUserId() { return userId; }
    public TicketStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void markPaid() {
        this.status = TicketStatus.PAID;
    }

    public boolean isReserved() {
        return status == TicketStatus.RESERVED;
    }

    public boolean isPaid() {
        return status == TicketStatus.PAID;
    }
}