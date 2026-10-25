package com.billetterie.payment_service;

import com.billetterie.payment_service.messaging.Topics;
import com.billetterie.payment_service.payment.Payment;
import com.billetterie.payment_service.payment.PaymentRepository;
import com.billetterie.payment_service.payment.PaymentSimulator;
import com.billetterie.payment_service.payment.PaymentStatus;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class PaymentFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.0.0");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PaymentSimulator simulator;

    // ---------- helpers ----------

    private void sendTicketReserved(UUID ticketId) throws Exception {
        String message = "{\"ticketId\":\"%s\",\"eventId\":\"evt-1\",\"userId\":\"%s\"}"
                .formatted(ticketId, UUID.randomUUID());
        kafkaTemplate.send(Topics.TICKET_RESERVED, ticketId.toString(), message).get(10, TimeUnit.SECONDS);
    }

    /** Lit les topics de reponse comme le ferait un autre service, et garde les messages de ce billet. */
    private List<String> awaitResponses(UUID ticketId, int expectedCount) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        List<String> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(Topics.PAYMENT_COMPLETED, Topics.PAYMENT_FAILED));
            long deadline = System.currentTimeMillis() + 30_000;
            while (received.size() < expectedCount && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (ticketId.toString().equals(record.key())) {
                        received.add(record.value());
                    }
                }
            }
        }
        return received;
    }

    // ---------- tests ----------

    @Test
    void approvedPayment_publishesPaymentCompleted_andIsStored() throws Exception {
        when(simulator.approve()).thenReturn(true);
        UUID ticketId = UUID.randomUUID();

        sendTicketReserved(ticketId);
        List<String> responses = awaitResponses(ticketId, 1);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0)).contains("paymentId");
        Payment payment = paymentRepository.findByTicketId(ticketId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void declinedPayment_publishesPaymentFailed_andIsStored() throws Exception {
        when(simulator.approve()).thenReturn(false);
        UUID ticketId = UUID.randomUUID();

        sendTicketReserved(ticketId);
        List<String> responses = awaitResponses(ticketId, 1);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0)).contains("reason");
        Payment payment = paymentRepository.findByTicketId(ticketId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void sameMessageDeliveredTwice_isPaidOnlyOnce_andTheSameAnswerIsRepeated() throws Exception {
        when(simulator.approve()).thenReturn(true);
        UUID ticketId = UUID.randomUUID();

        sendTicketReserved(ticketId);
        sendTicketReserved(ticketId);
        List<String> responses = awaitResponses(ticketId, 2);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0)).isEqualTo(responses.get(1));   // meme paymentId
        verify(simulator, times(1)).approve();                      // on n'a "paye" qu'une fois
    }

    @Test
    void anUnreadableMessage_doesNotBlockTheFollowingOnes() throws Exception {
        when(simulator.approve()).thenReturn(true);
        UUID ticketId = UUID.randomUUID();

        kafkaTemplate.send(Topics.TICKET_RESERVED, "bad", "ceci n'est pas du json").get(10, TimeUnit.SECONDS);
        sendTicketReserved(ticketId);
        List<String> responses = awaitResponses(ticketId, 1);

        assertThat(responses).hasSize(1);
    }
}