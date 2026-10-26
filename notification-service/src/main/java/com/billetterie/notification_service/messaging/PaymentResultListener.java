package com.billetterie.notification_service.messaging;

import com.billetterie.notification_service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PaymentResultListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    private final JsonMapper jsonMapper;
    private final NotificationService notificationService;

    public PaymentResultListener(JsonMapper jsonMapper, NotificationService notificationService) {
        this.jsonMapper = jsonMapper;
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMPLETED)
    public void onPaymentCompleted(String message) {
        PaymentCompletedEvent event = read(message, PaymentCompletedEvent.class);
        if (event == null || event.ticketId() == null || event.userId() == null) {
            log.warn("Ignoring incomplete payment-completed message: {}", message);
            return;
        }
        notificationService.paymentCompleted(event);
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED)
    public void onPaymentFailed(String message) {
        PaymentFailedEvent event = read(message, PaymentFailedEvent.class);
        if (event == null || event.ticketId() == null || event.userId() == null) {
            log.warn("Ignoring incomplete payment-failed message: {}", message);
            return;
        }
        notificationService.paymentFailed(event);
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