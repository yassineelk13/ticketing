package com.billetterie.ticket_service.ticket;

import com.billetterie.ticket_service.client.EventClient;
import com.billetterie.ticket_service.client.EventInfo;
import com.billetterie.ticket_service.exception.SoldOutException;
import com.billetterie.ticket_service.exception.TicketNotFoundException;
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

    private TicketService ticketService;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TransactionTemplate transaction = new TransactionTemplate(mock(PlatformTransactionManager.class));
        ticketService = new TicketService(ticketRepository, stockRepository, eventClient, transaction);
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
        when(stockRepository.save(any(EventStock.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockRepository.findById(EVENT_ID)).thenReturn(Optional.of(new EventStock(EVENT_ID, 50)));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.reserve(EVENT_ID, userId);

        ArgumentCaptor<EventStock> captor = ArgumentCaptor.forClass(EventStock.class);
        verify(stockRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalSeats()).isEqualTo(50);
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
}