package com.billetterie.ticket_service.ticket;

import com.billetterie.ticket_service.client.EventClient;
import com.billetterie.ticket_service.client.EventInfo;
import com.billetterie.ticket_service.exception.SoldOutException;
import com.billetterie.ticket_service.exception.TicketNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final EventStockRepository stockRepository;
    private final EventClient eventClient;
    private final TransactionTemplate transaction;

    public TicketService(TicketRepository ticketRepository,
                         EventStockRepository stockRepository,
                         EventClient eventClient,
                         TransactionTemplate transaction) {
        this.ticketRepository = ticketRepository;
        this.stockRepository = stockRepository;
        this.eventClient = eventClient;
        this.transaction = transaction;
    }

    public Ticket reserve(String eventId, UUID userId) {
        // 1. hors transaction : on connait la capacite de l'evenement (appel HTTP eventuel)
        ensureStockExists(eventId);

        // 2. transaction courte : lire le stock, le diminuer, enregistrer le billet
        return transaction.execute(status -> {
            EventStock stock = stockRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalStateException("Stock missing for event " + eventId));

            if (!stock.hasAvailableSeats()) {
                throw new SoldOutException(eventId);
            }

            stock.reserveSeat();
            return ticketRepository.save(new Ticket(eventId, userId));
        });
    }

    public List<Ticket> getMyTickets(UUID userId) {
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public void cancel(UUID ticketId, UUID userId) {
        transaction.executeWithoutResult(status -> {
            // un billet qui n'est pas a toi = "introuvable" (on ne revele pas qu'il existe)
            Ticket ticket = ticketRepository.findById(ticketId)
                    .filter(t -> t.getUserId().equals(userId))
                    .orElseThrow(() -> new TicketNotFoundException(ticketId));

            if (ticket.isCancelled()) {
                return; // deja annule : on ne rend pas la place deux fois
            }

            ticket.cancel();
            stockRepository.findById(ticket.getEventId()).ifPresent(EventStock::releaseSeat);
        });
    }

    private void ensureStockExists(String eventId) {
        if (stockRepository.existsById(eventId)) {
            return;
        }
        EventInfo event = eventClient.getEvent(eventId);
        stockRepository.save(new EventStock(eventId, event.totalSeats()));
    }
}