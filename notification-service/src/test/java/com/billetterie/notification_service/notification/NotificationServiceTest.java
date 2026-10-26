package com.billetterie.notification_service.notification;

import com.billetterie.notification_service.messaging.PaymentCompletedEvent;
import com.billetterie.notification_service.messaging.PaymentFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationSender sender;

    private NotificationService service;

    private final UUID ticketId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NotificationService(sender, new RecentMessages(100));
    }

    private PaymentCompletedEvent completed() {
        return new PaymentCompletedEvent(ticketId, "evt-1", userId, UUID.randomUUID());
    }

    private PaymentFailedEvent failed() {
        return new PaymentFailedEvent(ticketId, "evt-1", userId, "Payment declined (simulated)");
    }

    @Test
    void paymentCompleted_sendsAConfirmationToTheUser() {
        service.paymentCompleted(completed());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(userId);
        assertThat(captor.getValue().ticketId()).isEqualTo(ticketId);
        assertThat(captor.getValue().subject()).contains("confirmed");
    }

    @Test
    void paymentCompleted_deliveredTwice_sendsOnlyOneNotification() {
        service.paymentCompleted(completed());
        service.paymentCompleted(completed());

        verify(sender, times(1)).send(any(Notification.class));
    }

    @Test
    void paymentFailed_sendsADeclinedNotificationWithTheReason() {
        service.paymentFailed(failed());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().subject()).contains("declined");
        assertThat(captor.getValue().body()).contains("Payment declined (simulated)");
    }

    @Test
    void ifSendingFails_theRetryIsNotMistakenForADuplicate() {
        doThrow(new RuntimeException("smtp down")).doNothing().when(sender).send(any(Notification.class));

        assertThatThrownBy(() -> service.paymentCompleted(completed())).isInstanceOf(RuntimeException.class);
        service.paymentCompleted(completed());

        verify(sender, times(2)).send(any(Notification.class));
    }
}