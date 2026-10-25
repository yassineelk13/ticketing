package com.billetterie.ticket_service.messaging;

import com.billetterie.ticket_service.ticket.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PaymentResultListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    private final JsonMapper jsonMapper;
    private final TicketService ticketService;

    public PaymentResultListener(JsonMapper jsonMapper, TicketService ticketService) {
        this.jsonMapper = jsonMapper;
        this.ticketService = ticketService;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMPLETED)
    public void onPaymentCompleted(String message) {
        PaymentCompletedEvent event = read(message, PaymentCompletedEvent.class);
        if (event == null || event.ticketId() == null) {
            log.warn("Ignoring incomplete payment-completed message: {}", message);
            return;
        }
        ticketService.confirmPayment(event.ticketId());
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED)
    public void onPaymentFailed(String message) {
        PaymentFailedEvent event = read(message, PaymentFailedEvent.class);
        if (event == null || event.ticketId() == null) {
            log.warn("Ignoring incomplete payment-failed message: {}", message);
            return;
        }
        ticketService.failPayment(event.ticketId());
    }

    private <T> T read(String message, Class<T> type) {
        try {
            return jsonMapper.readValue(message, type);
        } catch (RuntimeException e) {
            log.warn("Ignoring unreadable message: {}", message);
            return null;
        }
    }
}