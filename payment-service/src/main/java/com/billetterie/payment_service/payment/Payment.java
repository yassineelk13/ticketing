package com.billetterie.payment_service.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_ticket", columnNames = "ticket_id")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {
        // requis par JPA
    }

    private Payment(UUID ticketId, String eventId, UUID userId, PaymentStatus status, String failureReason) {
        this.ticketId = ticketId;
        this.eventId = eventId;
        this.userId = userId;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = Instant.now();
    }

    public static Payment completed(UUID ticketId, String eventId, UUID userId) {
        return new Payment(ticketId, eventId, userId, PaymentStatus.COMPLETED, null);
    }

    public static Payment failed(UUID ticketId, String eventId, UUID userId, String reason) {
        return new Payment(ticketId, eventId, userId, PaymentStatus.FAILED, reason);
    }

    public UUID getId() { return id; }
    public UUID getTicketId() { return ticketId; }
    public String getEventId() { return eventId; }
    public UUID getUserId() { return userId; }
    public PaymentStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
}