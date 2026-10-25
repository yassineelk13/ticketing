package com.billetterie.ticket_service;

import com.billetterie.ticket_service.client.EventClient;
import com.billetterie.ticket_service.messaging.OutboxRepository;
import com.billetterie.ticket_service.messaging.Topics;
import com.billetterie.ticket_service.ticket.EventStock;
import com.billetterie.ticket_service.ticket.EventStockRepository;
import com.billetterie.ticket_service.ticket.Ticket;
import com.billetterie.ticket_service.ticket.TicketRepository;
import com.billetterie.ticket_service.ticket.TicketService;
import com.billetterie.ticket_service.ticket.TicketStatus;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.jwt.secret=kafka-test-secret-at-least-32-bytes-long!!",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "app.outbox.relay.interval-ms=200"
})
@Import(TestcontainersConfiguration.class)
@Testcontainers
class PaymentResultsIntegrationTest {

    private static final String EVENT_ID = "kafka-event";
    private static final int SEATS = 3;

    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.0.0");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private EventStockRepository stockRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private EventClient eventClient;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        outboxRepository.deleteAll();
        stockRepository.deleteAll();
        stockRepository.save(new EventStock(EVENT_ID, SEATS));
    }

    // ---------- helpers ----------

    private Ticket reserve() {
        return ticketService.reserve(EVENT_ID, UUID.randomUUID());
    }

    /** Tout part sur la partition 0 : les messages sont donc traites dans l'ordre exact de l'envoi. */
    private void send(String topic, UUID ticketId, String json) throws Exception {
        kafkaTemplate.send(topic, 0, ticketId.toString(), json).get(10, TimeUnit.SECONDS);
    }

    private static String completed(UUID ticketId) {
        return "{\"ticketId\":\"%s\",\"eventId\":\"%s\",\"userId\":\"%s\",\"paymentId\":\"%s\"}"
                .formatted(ticketId, EVENT_ID, UUID.randomUUID(), UUID.randomUUID());
    }

    private static String failed(UUID ticketId) {
        return "{\"ticketId\":\"%s\",\"eventId\":\"%s\",\"userId\":\"%s\",\"reason\":\"declined\"}"
                .formatted(ticketId, EVENT_ID, UUID.randomUUID());
    }

    private boolean await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100);
        }
        return condition.getAsBoolean();
    }

    private TicketStatus statusOf(UUID ticketId) {
        return ticketRepository.findById(ticketId).orElseThrow().getStatus();
    }

    private int availableSeats() {
        return stockRepository.findById(EVENT_ID).orElseThrow().getAvailableSeats();
    }

    private List<String> readTicketReserved(UUID ticketId) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        List<String> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(Topics.TICKET_RESERVED));
            long deadline = System.currentTimeMillis() + 30_000;
            while (received.isEmpty() && System.currentTimeMillis() < deadline) {
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
    void aReservation_isPublishedOnTheTicketReservedTopic_throughTheOutbox() throws Exception {
        Ticket ticket = reserve();

        List<String> messages = readTicketReserved(ticket.getId());

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).contains(ticket.getId().toString()).contains(EVENT_ID);
        assertThat(await(() -> outboxRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc().isEmpty()))
                .as("outbox emptied by the relay").isTrue();
    }

    @Test
    void paymentCompleted_marksTheTicketAsPaid() throws Exception {
        Ticket ticket = reserve();

        send(Topics.PAYMENT_COMPLETED, ticket.getId(), completed(ticket.getId()));

        assertThat(await(() -> statusOf(ticket.getId()) == TicketStatus.PAID)).isTrue();
        assertThat(availableSeats()).isEqualTo(SEATS - 1);
    }

    @Test
    void paymentFailed_cancelsTheTicketAndGivesTheSeatBack() throws Exception {
        Ticket ticket = reserve();
        assertThat(availableSeats()).isEqualTo(SEATS - 1);

        send(Topics.PAYMENT_FAILED, ticket.getId(), failed(ticket.getId()));

        assertThat(await(() -> statusOf(ticket.getId()) == TicketStatus.CANCELLED)).isTrue();
        assertThat(availableSeats()).isEqualTo(SEATS);
    }

    @Test
    void paymentFailedDeliveredTwice_releasesTheSeatOnlyOnce() throws Exception {
        Ticket a = reserve();
        Ticket b = reserve();
        reserve();
        assertThat(availableSeats()).isZero();

        // A est annule deux fois, puis B : meme partition, donc traites dans cet ordre
        send(Topics.PAYMENT_FAILED, a.getId(), failed(a.getId()));
        send(Topics.PAYMENT_FAILED, a.getId(), failed(a.getId()));
        send(Topics.PAYMENT_FAILED, b.getId(), failed(b.getId()));

        assertThat(await(() -> statusOf(b.getId()) == TicketStatus.CANCELLED)).isTrue();
        // A et B rendent chacun une place : 2. Si le doublon comptait, on aurait 3.
        assertThat(availableSeats()).isEqualTo(2);
    }

    @Test
    void aPaymentResultForAnUnknownTicket_isIgnored_andDoesNotBlockTheNext() throws Exception {
        Ticket ticket = reserve();

        UUID ghost = UUID.randomUUID();
        send(Topics.PAYMENT_COMPLETED, ghost, completed(ghost));
        send(Topics.PAYMENT_COMPLETED, ticket.getId(), completed(ticket.getId()));

        assertThat(await(() -> statusOf(ticket.getId()) == TicketStatus.PAID)).isTrue();
    }
}