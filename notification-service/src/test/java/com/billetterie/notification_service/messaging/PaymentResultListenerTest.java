package com.billetterie.notification_service.messaging;

import com.billetterie.notification_service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentResultListenerTest {

    @Mock
    private NotificationService notificationService;

    private PaymentResultListener listener;

    private final UUID ticketId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new PaymentResultListener(JsonMapper.builder().build(), notificationService);
    }

    @Test
    void paymentCompleted_isPassedToTheService() {
        String message = "{\"ticketId\":\"%s\",\"eventId\":\"evt-1\",\"userId\":\"%s\",\"paymentId\":\"%s\"}"
                .formatted(ticketId, userId, paymentId);

        listener.onPaymentCompleted(message);

        verify(notificationService).paymentCompleted(new PaymentCompletedEvent(ticketId, "evt-1", userId, paymentId));
    }

    @Test
    void paymentFailed_isPassedToTheService() {
        String message = "{\"ticketId\":\"%s\",\"eventId\":\"evt-1\",\"userId\":\"%s\",\"reason\":\"declined\"}"
                .formatted(ticketId, userId);

        listener.onPaymentFailed(message);

        verify(notificationService).paymentFailed(new PaymentFailedEvent(ticketId, "evt-1", userId, "declined"));
    }

    @Test
    void unreadableOrIncompleteMessages_areIgnored() {
        listener.onPaymentCompleted("ceci n'est pas du json");
        listener.onPaymentFailed("ceci n'est pas du json");
        listener.onPaymentCompleted("{\"eventId\":\"evt-1\"}");
        listener.onPaymentFailed("{\"eventId\":\"evt-1\"}");

        verifyNoInteractions(notificationService);
    }
}