package com.billetterie.ticket_service.ticket;

import com.billetterie.ticket_service.client.EventClient;
import com.billetterie.ticket_service.client.EventInfo;
import com.billetterie.ticket_service.exception.SoldOutException;
import com.billetterie.ticket_service.exception.TicketNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

    private static final int MAX_ATTEMPTS = 10;

    public Ticket reserve(String eventId, UUID userId) {
        // 1. hors transaction : on connait la capacite de l'evenement (appel HTTP eventuel)
        ensureStockExists(eventId);

        // 2. transaction courte, rejouee si quelqu'un a modifie le stock entre-temps
        for (int attempt = 1; ; attempt++) {
            try {
                return reserveOnce(eventId, userId);
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                backoff(attempt);
            }
        }
    }

    private Ticket reserveOnce(String eventId, UUID userId) {
        return transaction.execute(status -> {
            EventStock stock = stockRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalStateException("Stock missing for event " + eventId));

            if (!stock.hasAvailableSeats()) {
                throw new SoldOutException(eventId);
            }

            stock.reserveSeat();
            Ticket ticket = ticketRepository.save(new Ticket(eventId, userId));
            stockRepository.flush(); // le conflit de version eclate ici, avant la fin de la transaction
            return ticket;
        });
    }

    private void backoff(int attempt) {
        try {
            // petite pause aleatoire, de plus en plus longue : evite que tout le monde reessaie au meme instant
            Thread.sleep(ThreadLocalRandom.current().nextLong(5L * attempt, 20L * attempt));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying reservation", ie);
        }
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
        stockRepository.insertIfAbsent(eventId, event.totalSeats());
    }
}