package com.billetterie.ticket_service.messaging;

import com.billetterie.ticket_service.ticket.TicketService;
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
    private TicketService ticketService;

    private PaymentResultListener listener;

    private final UUID ticketId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new PaymentResultListener(JsonMapper.builder().build(), ticketService);
    }

    @Test
    void paymentCompleted_confirmsTheTicket() {
        String message = "{\"ticketId\":\"%s\",\"eventId\":\"evt-1\",\"userId\":\"%s\",\"paymentId\":\"%s\"}"
                .formatted(ticketId, userId, UUID.randomUUID());

        listener.onPaymentCompleted(message);

        verify(ticketService).confirmPayment(ticketId);
    }

    @Test
    void paymentFailed_failsTheTicket() {
        String message = "{\"ticketId\":\"%s\",\"eventId\":\"evt-1\",\"userId\":\"%s\",\"reason\":\"declined\"}"
                .formatted(ticketId, userId);

        listener.onPaymentFailed(message);

        verify(ticketService).failPayment(ticketId);
    }

    @Test
    void unreadableMessages_areIgnored() {
        listener.onPaymentCompleted("ceci n'est pas du json");
        listener.onPaymentFailed("ceci n'est pas du json");

        verifyNoInteractions(ticketService);
    }

    @Test
    void messagesWithoutATicketId_areIgnored() {
        listener.onPaymentCompleted("{\"eventId\":\"evt-1\"}");
        listener.onPaymentFailed("{\"eventId\":\"evt-1\"}");

        verifyNoInteractions(ticketService);
    }
}