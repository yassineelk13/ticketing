package com.billetterie.ticket_service.exception;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(String eventId) {
        super("Event not found: " + eventId);
    }
}