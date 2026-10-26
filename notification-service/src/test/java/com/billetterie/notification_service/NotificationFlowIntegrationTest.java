package com.billetterie.notification_service;

import com.billetterie.notification_service.messaging.Topics;
import com.billetterie.notification_service.notification.Notification;
import com.billetterie.notification_service.notification.NotificationSender;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@Testcontainers
class NotificationFlowIntegrationTest {

    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.0.0");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private NotificationSender sender;

    private final List<Notification> sent = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        sent.clear();
        doAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return null;
        }).when(sender).send(any(Notification.class));
    }

    // ---------- helpers ----------

    /** Tout part sur la partition 0 : les messages sont traites dans l'ordre exact de l'envoi. */
    private void send(String topic, UUID ticketId, String json) throws Exception {
        kafkaTemplate.send(topic, 0, ticketId.toString(), json).get(10, TimeUnit.SECONDS);
    }

    private static String completed(UUID ticketId) {
        return "{\"ticketId\":\"%s\",\"eventId\":\"evt-1\",\"userId\":\"%s\",\"paymentId\":\"%s\"}"
                .formatted(ticketId, UUID.randomUUID(), UUID.randomUUID());
    }

    private static String failed(UUID ticketId) {
        return "{\"ticketId\":\"%s\",\"eventId\":\"evt-1\",\"userId\":\"%s\",\"reason\":\"declined\"}"
                .formatted(ticketId, UUID.randomUUID());
    }

    private List<Notification> awaitNotifications(UUID ticketId, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        List<Notification> matching;
        do {
            matching = sent.stream().filter(n -> n.ticketId().equals(ticketId)).toList();
            if (matching.size() >= expected) {
                break;
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);
        return matching;
    }

    // ---------- tests ----------

    @Test
    void paymentCompleted_triggersAConfirmation() throws Exception {
        UUID ticketId = UUID.randomUUID();

        send(Topics.PAYMENT_COMPLETED, ticketId, completed(ticketId));

        List<Notification> notifications = awaitNotifications(ticketId, 1);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).subject()).contains("confirmed");
    }

    @Test
    void paymentFailed_triggersADeclinedNotification() throws Exception {
        UUID ticketId = UUID.randomUUID();

        send(Topics.PAYMENT_FAILED, ticketId, failed(ticketId));

        List<Notification> notifications = awaitNotifications(ticketId, 1);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).subject()).contains("declined");
    }

    @Test
    void sameMessageDeliveredTwice_sendsOnlyOneNotification() throws Exception {
        UUID duplicated = UUID.randomUUID();
        UUID sentinel = UUID.randomUUID();

        send(Topics.PAYMENT_COMPLETED, duplicated, completed(duplicated));
        send(Topics.PAYMENT_COMPLETED, duplicated, completed(duplicated));
        send(Topics.PAYMENT_COMPLETED, sentinel, completed(sentinel));

        // quand le dernier message est traite, les deux premiers l'ont forcement ete aussi
        assertThat(awaitNotifications(sentinel, 1)).hasSize(1);
        assertThat(sent.stream().filter(n -> n.ticketId().equals(duplicated))).hasSize(1);
    }

    @Test
    void anUnreadableMessage_doesNotBlockTheFollowingOnes() throws Exception {
        UUID ticketId = UUID.randomUUID();

        kafkaTemplate.send(Topics.PAYMENT_COMPLETED, 0, "bad", "ceci n'est pas du json").get(10, TimeUnit.SECONDS);
        send(Topics.PAYMENT_COMPLETED, ticketId, completed(ticketId));

        assertThat(awaitNotifications(ticketId, 1)).hasSize(1);
    }
}