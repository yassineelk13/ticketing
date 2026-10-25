package com.billetterie.ticket_service.ticket;

import com.billetterie.ticket_service.client.EventClient;
import com.billetterie.ticket_service.client.EventInfo;
import com.billetterie.ticket_service.exception.SoldOutException;
import com.billetterie.ticket_service.exception.TicketNotFoundException;
import com.billetterie.ticket_service.messaging.OutboxWriter;
import com.billetterie.ticket_service.messaging.TicketReservedEvent;
import com.billetterie.ticket_service.messaging.Topics;
import static org.mockito.ArgumentMatchers.eq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;



@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    private static final String EVENT_ID = "event-1";

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private EventStockRepository stockRepository;

    @Mock
    private EventClient eventClient;

    @Mock
    private OutboxWriter outbox;

    private TicketService ticketService;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TransactionTemplate transaction = new TransactionTemplate(mock(PlatformTransactionManager.class));
        ticketService = new TicketService(ticketRepository, stockRepository, eventClient, transaction, outbox);
    }

    @Test
    void reserve_decrementsStockAndCreatesReservedTicket() {
        EventStock stock = new EventStock(EVENT_ID, 2);
        when(stockRepository.existsById(EVENT_ID)).thenReturn(true);
        when(stockRepository.findById(EVENT_ID)).thenReturn(Optional.of(stock));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket ticket = ticketService.reserve(EVENT_ID, userId);

        assertThat(stock.getAvailableSeats()).isEqualTo(1);
        assertThat(ticket.getEventId()).isEqualTo(EVENT_ID);
        assertThat(ticket.getUserId()).isEqualTo(userId);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESERVED);
        verify(eventClient, never()).getEvent(any());
    }

    @Test
    void reserve_whenNoSeatLeft_throwsSoldOutAndSavesNothing() {
        EventStock stock = new EventStock(EVENT_ID, 1);
        stock.reserveSeat();
        when(stockRepository.existsById(EVENT_ID)).thenReturn(true);
        when(stockRepository.findById(EVENT_ID)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> ticketService.reserve(EVENT_ID, userId))
                .isInstanceOf(SoldOutException.class);

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void reserve_forUnknownEvent_createsStockFromEventServiceCapacity() {
        when(stockRepository.existsById(EVENT_ID)).thenReturn(false);
        when(eventClient.getEvent(EVENT_ID)).thenReturn(new EventInfo(EVENT_ID, 50));
        when(stockRepository.findById(EVENT_ID)).thenReturn(Optional.of(new EventStock(EVENT_ID, 50)));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.reserve(EVENT_ID, userId);

        verify(stockRepository).insertIfAbsent(EVENT_ID, 50);
    }

    @Test
    void cancel_marksTicketCancelledAndReleasesTheSeat() {
        UUID ticketId = UUID.randomUUID();
        EventStock stock = new EventStock(EVENT_ID, 2);
        stock.reserveSeat();
        Ticket ticket = new Ticket(EVENT_ID, userId);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(stockRepository.findById(EVENT_ID)).thenReturn(Optional.of(stock));

        ticketService.cancel(ticketId, userId);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(stock.getAvailableSeats()).isEqualTo(2);
    }

    @Test
    void cancel_ticketOfAnotherUser_isTreatedAsNotFound() {
        UUID ticketId = UUID.randomUUID();
        Ticket someoneElsesTicket = new Ticket(EVENT_ID, UUID.randomUUID());
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(someoneElsesTicket));

        assertThatThrownBy(() -> ticketService.cancel(ticketId, userId))
                .isInstanceOf(TicketNotFoundException.class);

        assertThat(someoneElsesTicket.getStatus()).isEqualTo(TicketStatus.RESERVED);
        verifyNoInteractions(stockRepository);
    }

    @Test
    void cancel_alreadyCancelledTicket_doesNotReleaseASecondSeat() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = new Ticket(EVENT_ID, userId);
        ticket.cancel();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ticketService.cancel(ticketId, userId);

        verifyNoInteractions(stockRepository);
    }

    @Test
    void cancel_unknownTicket_throwsNotFound() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.cancel(ticketId, userId))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void reserve_writesATicketReservedMessageInTheOutbox() {
        EventStock stock = new EventStock(EVENT_ID, 2);
        when(stockRepository.existsById(EVENT_ID)).thenReturn(true);
        when(stockRepository.findById(EVENT_ID)).thenReturn(Optional.of(stock));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket ticket = ticketService.reserve(EVENT_ID, userId);

        verify(outbox).add(eq(Topics.TICKET_RESERVED), any(),
                eq(new TicketReservedEvent(ticket.getId(), EVENT_ID, userId)));
    }

    @Test
    void reserve_whenSoldOut_writesNothingInTheOutbox() {
        EventStock stock = new EventStock(EVENT_ID, 1);
        stock.reserveSeat();
        when(stockRepository.existsById(EVENT_ID)).thenReturn(true);
        when(stockRepository.findById(EVENT_ID)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> ticketService.reserve(EVENT_ID, userId))
                .isInstanceOf(SoldOutException.class);

        verifyNoInteractions(outbox);
    }

    @Test
    void confirmPayment_marksAReservedTicketAsPaid() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = new Ticket(EVENT_ID, userId);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ticketService.confirmPayment(ticketId);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);
    }

    @Test
    void confirmPayment_forUnknownTicket_isIgnored() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        ticketService.confirmPayment(ticketId);

        verifyNoInteractions(stockRepository);
    }

    @Test
    void confirmPayment_forACancelledTicket_keepsItCancelled() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = new Ticket(EVENT_ID, userId);
        ticket.cancel();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ticketService.confirmPayment(ticketId);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }

    @Test
    void failPayment_cancelsTheTicketAndReleasesTheSeat() {
        UUID ticketId = UUID.randomUUID();
        EventStock stock = new EventStock(EVENT_ID, 2);
        stock.reserveSeat();
        Ticket ticket = new Ticket(EVENT_ID, userId);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(stockRepository.findById(EVENT_ID)).thenReturn(Optional.of(stock));

        ticketService.failPayment(ticketId);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(stock.getAvailableSeats()).isEqualTo(2);
    }

    @Test
    void failPayment_whenAlreadyCancelled_doesNotReleaseASecondSeat() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = new Ticket(EVENT_ID, userId);
        ticket.cancel();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ticketService.failPayment(ticketId);

        verifyNoInteractions(stockRepository);
    }

    @Test
    void failPayment_forAPaidTicket_changesNothing() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = new Ticket(EVENT_ID, userId);
        ticket.markPaid();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ticketService.failPayment(ticketId);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);
        verifyNoInteractions(stockRepository);
    }
}