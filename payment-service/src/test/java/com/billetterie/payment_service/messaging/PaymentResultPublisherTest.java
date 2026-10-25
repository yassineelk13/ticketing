package com.billetterie.payment_service.messaging;

import com.billetterie.payment_service.payment.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentResultPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private PaymentResultPublisher publisher;

    private final UUID ticketId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        publisher = new PaymentResultPublisher(kafkaTemplate, JsonMapper.builder().build());
    }

    @Test
    void completedPayment_isPublishedOnPaymentCompletedWithTheTicketIdAsKey() {
        Payment payment = Payment.completed(ticketId, "evt-1", userId);
        ReflectionTestUtils.setField(payment, "id", paymentId);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(payment);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(Topics.PAYMENT_COMPLETED), eq(ticketId.toString()), json.capture());
        assertThat(json.getValue()).contains(ticketId.toString()).contains(paymentId.toString());
    }

    @Test
    void failedPayment_isPublishedOnPaymentFailedWithTheReason() {
        Payment payment = Payment.failed(ticketId, "evt-1", userId, "Payment declined (simulated)");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(payment);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(Topics.PAYMENT_FAILED), eq(ticketId.toString()), json.capture());
        assertThat(json.getValue()).contains(ticketId.toString()).contains("Payment declined (simulated)");
    }

    @Test
    void whenKafkaRejectsTheMessage_thePublisherFails() {
        Payment payment = Payment.completed(ticketId, "evt-1", userId);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThatThrownBy(() -> publisher.publish(payment)).isInstanceOf(IllegalStateException.class);
    }
}