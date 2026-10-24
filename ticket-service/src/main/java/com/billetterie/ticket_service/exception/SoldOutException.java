package com.billetterie.ticket_service.exception;

public class SoldOutException extends RuntimeException {

    public SoldOutException(String eventId) {
        super("No seats left for event: " + eventId);
    }
}