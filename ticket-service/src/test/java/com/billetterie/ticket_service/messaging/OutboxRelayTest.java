package com.billetterie.ticket_service.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outboxRepository, kafkaTemplate);
    }

    private static OutboxMessage message(String key) {
        OutboxMessage message = new OutboxMessage(Topics.TICKET_RESERVED, key, "{\"k\":\"" + key + "\"}");
        ReflectionTestUtils.setField(message, "id", UUID.randomUUID());
        return message;
    }

    @Test
    void pendingMessages_areSentInOrderAndMarkedAsSent() {
        OutboxMessage first = message("a");
        OutboxMessage second = message("b");
        when(outboxRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();

        InOrder order = inOrder(kafkaTemplate, outboxRepository);
        order.verify(kafkaTemplate).send(Topics.TICKET_RESERVED, "a", first.getPayload());
        order.verify(outboxRepository).markSent(eq(first.getId()), any());
        order.verify(kafkaTemplate).send(Topics.TICKET_RESERVED, "b", second.getPayload());
        order.verify(outboxRepository).markSent(eq(second.getId()), any());
    }

    @Test
    void whenKafkaFails_theMessageStaysPending_andTheFollowingOnesAreNotSent() {
        OutboxMessage first = message("a");
        OutboxMessage second = message("b");
        when(outboxRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        relay.publishPending();

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
        verify(outboxRepository, never()).markSent(any(), any());
    }

    @Test
    void afterKafkaRecovers_theMessageIsSentOnTheNextRun() {
        OutboxMessage pending = message("a");
        when(outboxRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(pending));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();   // Kafka est en panne
        relay.publishPending();   // Kafka est revenu

        verify(outboxRepository, times(1)).markSent(eq(pending.getId()), any());
    }
}