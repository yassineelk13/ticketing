package com.billetterie.payment_service.messaging;

public final class Topics {

    public static final String TICKET_RESERVED = "ticket-reserved";
    public static final String PAYMENT_COMPLETED = "payment-completed";
    public static final String PAYMENT_FAILED = "payment-failed";

    private Topics() {
    }
}