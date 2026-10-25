package com.billetterie.payment_service.payment;

import com.billetterie.payment_service.messaging.TicketReservedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentSimulator simulator;

    @InjectMocks
    private PaymentService paymentService;

    private final UUID ticketId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final TicketReservedEvent event = new TicketReservedEvent(ticketId, "evt-1", userId);

    @Test
    void approvedPayment_isSavedAsCompleted() {
        when(paymentRepository.findByTicketId(ticketId)).thenReturn(Optional.empty());
        when(simulator.approve()).thenReturn(true);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = paymentService.process(event);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getTicketId()).isEqualTo(ticketId);
        assertThat(payment.getUserId()).isEqualTo(userId);
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    void declinedPayment_isSavedAsFailedWithAReason() {
        when(paymentRepository.findByTicketId(ticketId)).thenReturn(Optional.empty());
        when(simulator.approve()).thenReturn(false);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = paymentService.process(event);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isNotBlank();
    }

    @Test
    void alreadyProcessedTicket_returnsTheExistingPaymentWithoutPayingAgain() {
        Payment existing = Payment.completed(ticketId, "evt-1", userId);
        when(paymentRepository.findByTicketId(ticketId)).thenReturn(Optional.of(existing));

        Payment result = paymentService.process(event);

        assertThat(result).isSameAs(existing);
        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
        verifyNoInteractions(simulator);
    }
}