package com.billetterie.notification_service.notification;

import com.billetterie.notification_service.messaging.PaymentCompletedEvent;
import com.billetterie.notification_service.messaging.PaymentFailedEvent;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationSender sender;
    private final RecentMessages recent;

    public NotificationService(NotificationSender sender, RecentMessages recent) {
        this.sender = sender;
        this.recent = recent;
    }

    public void paymentCompleted(PaymentCompletedEvent event) {
        String key = "completed:" + event.ticketId();
        if (recent.contains(key)) {
            return;
        }
        sender.send(new Notification(event.userId(), event.ticketId(),
                "Your ticket is confirmed",
                "Your payment was accepted. Ticket " + event.ticketId()
                        + " for event " + event.eventId() + " is confirmed."));
        recent.add(key);
    }

    public void paymentFailed(PaymentFailedEvent event) {
        String key = "failed:" + event.ticketId();
        if (recent.contains(key)) {
            return;
        }
        sender.send(new Notification(event.userId(), event.ticketId(),
                "Your payment was declined",
                "Your payment for event " + event.eventId() + " was declined (" + event.reason()
                        + "). Your reservation was cancelled and the seat was released."));
        recent.add(key);
    }
}