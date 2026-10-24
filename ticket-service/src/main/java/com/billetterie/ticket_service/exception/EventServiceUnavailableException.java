package com.billetterie.ticket_service.exception;

public class EventServiceUnavailableException extends RuntimeException {

    public EventServiceUnavailableException(Throwable cause) {
        super("Event service is unavailable", cause);
    }
}