package com.billetterie.payment_service.payment;

import com.billetterie.payment_service.messaging.TicketReservedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentSimulator simulator;

    public PaymentService(PaymentRepository paymentRepository, PaymentSimulator simulator) {
        this.paymentRepository = paymentRepository;
        this.simulator = simulator;
    }

    @Transactional
    public Payment process(TicketReservedEvent event) {
        return paymentRepository.findByTicketId(event.ticketId())
                .orElseGet(() -> paymentRepository.saveAndFlush(decide(event)));
    }

    private Payment decide(TicketReservedEvent event) {
        if (simulator.approve()) {
            return Payment.completed(event.ticketId(), event.eventId(), event.userId());
        }
        return Payment.failed(event.ticketId(), event.eventId(), event.userId(),
                "Payment declined (simulated)");
    }
}