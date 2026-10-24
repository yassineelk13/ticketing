package com.billetterie.ticket_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ProblemDetail handleEventNotFound(EventNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Event not found", ex.getMessage());
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ProblemDetail handleTicketNotFound(TicketNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Ticket not found", ex.getMessage());
    }

    @ExceptionHandler(SoldOutException.class)
    public ProblemDetail handleSoldOut(SoldOutException ex) {
        return problem(HttpStatus.CONFLICT, "Sold out", ex.getMessage());
    }

    @ExceptionHandler(EventServiceUnavailableException.class)
    public ProblemDetail handleEventServiceUnavailable(EventServiceUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Too many concurrent requests",
                "The reservation could not be completed, please try again");
    }
}