package com.billetterie.payment_service.messaging;

import com.billetterie.payment_service.payment.Payment;
import com.billetterie.payment_service.payment.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TicketReservedListener {

    private static final Logger log = LoggerFactory.getLogger(TicketReservedListener.class);

    private final JsonMapper jsonMapper;
    private final PaymentService paymentService;
    private final PaymentResultPublisher publisher;

    public TicketReservedListener(JsonMapper jsonMapper,
                                  PaymentService paymentService,
                                  PaymentResultPublisher publisher) {
        this.jsonMapper = jsonMapper;
        this.paymentService = paymentService;
        this.publisher = publisher;
    }

    @KafkaListener(topics = Topics.TICKET_RESERVED)
    public void onTicketReserved(String message) {
        TicketReservedEvent event;
        try {
            event = jsonMapper.readValue(message, TicketReservedEvent.class);
        } catch (RuntimeException e) {
            log.warn("Ignoring unreadable ticket-reserved message: {}", message);
            return;
        }
        if (event == null || event.ticketId() == null || event.eventId() == null || event.userId() == null) {
            log.warn("Ignoring incomplete ticket-reserved message: {}", message);
            return;
        }

        Payment payment = paymentService.process(event);   // transaction courte : decider et enregistrer
        publisher.publish(payment);                         // apres le commit : prevenir les autres services
    }
}