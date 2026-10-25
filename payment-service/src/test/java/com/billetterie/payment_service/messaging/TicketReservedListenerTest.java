package com.billetterie.payment_service.messaging;

import com.billetterie.payment_service.payment.Payment;
import com.billetterie.payment_service.payment.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketReservedListenerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentResultPublisher publisher;

    private TicketReservedListener listener;

    private final UUID ticketId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new TicketReservedListener(JsonMapper.builder().build(), paymentService, publisher);
    }

    @Test
    void validMessage_isProcessedAndTheResultIsPublished() {
        String message = "{\"ticketId\":\"%s\",\"eventId\":\"evt-1\",\"userId\":\"%s\"}".formatted(ticketId, userId);
        Payment payment = Payment.completed(ticketId, "evt-1", userId);
        when(paymentService.process(new TicketReservedEvent(ticketId, "evt-1", userId))).thenReturn(payment);

        listener.onTicketReserved(message);

        verify(publisher).publish(payment);
    }

    @Test
    void messageThatIsNotJson_isIgnored() {
        listener.onTicketReserved("ceci n'est pas du json");

        verifyNoInteractions(paymentService, publisher);
    }

    @Test
    void messageWithMissingFields_isIgnored() {
        listener.onTicketReserved("{\"ticketId\":\"%s\",\"eventId\":\"evt-1\"}".formatted(ticketId));

        verifyNoInteractions(paymentService, publisher);
    }
}