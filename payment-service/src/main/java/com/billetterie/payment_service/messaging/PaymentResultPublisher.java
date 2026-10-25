package com.billetterie.payment_service.messaging;

import com.billetterie.payment_service.payment.Payment;
import com.billetterie.payment_service.payment.PaymentStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class PaymentResultPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    public PaymentResultPublisher(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
    }

    public void publish(Payment payment) {
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            send(Topics.PAYMENT_COMPLETED, payment.getTicketId().toString(),
                    new PaymentCompletedEvent(payment.getTicketId(), payment.getEventId(),
                            payment.getUserId(), payment.getId()));
        } else {
            send(Topics.PAYMENT_FAILED, payment.getTicketId().toString(),
                    new PaymentFailedEvent(payment.getTicketId(), payment.getEventId(),
                            payment.getUserId(), payment.getFailureReason()));
        }
    }

    private void send(String topic, String key, Object event) {
        String json = jsonMapper.writeValueAsString(event);
        try {
            kafkaTemplate.send(topic, key, json).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing to " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Could not publish to " + topic, e);
        }
    }
}