package com.billetterie.ticket_service.ticket;

import com.billetterie.ticket_service.client.EventClient;
import com.billetterie.ticket_service.client.EventInfo;
import com.billetterie.ticket_service.exception.SoldOutException;
import com.billetterie.ticket_service.exception.TicketNotFoundException;
import com.billetterie.ticket_service.messaging.OutboxWriter;
import com.billetterie.ticket_service.messaging.TicketReservedEvent;
import com.billetterie.ticket_service.messaging.Topics;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TicketService {

    private static final int MAX_ATTEMPTS = 10;

    private final TicketRepository ticketRepository;
    private final EventStockRepository stockRepository;
    private final EventClient eventClient;
    private final TransactionTemplate transaction;
    private final OutboxWriter outbox;
    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    public TicketService(TicketRepository ticketRepository,
                         EventStockRepository stockRepository,
                         EventClient eventClient,
                         TransactionTemplate transaction,
                         OutboxWriter outbox) {
        this.ticketRepository = ticketRepository;
        this.stockRepository = stockRepository;
        this.eventClient = eventClient;
        this.transaction = transaction;
        this.outbox = outbox;
    }

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

            // le message part dans la meme transaction que le billet : les deux ou aucun
            outbox.add(Topics.TICKET_RESERVED, ticket.getId(),
                    new TicketReservedEvent(ticket.getId(), eventId, userId));

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

    public void confirmPayment(UUID ticketId) {
        transaction.executeWithoutResult(status ->
                ticketRepository.findById(ticketId).ifPresentOrElse(ticket -> {
                    if (ticket.isReserved()) {
                        ticket.markPaid();
                    } else if (ticket.isCancelled()) {
                        log.warn("Payment completed for ticket {} which was cancelled: a refund would be needed",
                                ticketId);
                    }
                    // deja PAID : message en double, rien a faire
                }, () -> log.warn("Payment completed for unknown ticket {}, ignored", ticketId)));
    }

    public void failPayment(UUID ticketId) {
        retryOnConflict(() -> transaction.executeWithoutResult(status ->
                ticketRepository.findById(ticketId).ifPresentOrElse(ticket -> {
                    if (ticket.isReserved()) {
                        ticket.cancel();
                        stockRepository.findById(ticket.getEventId()).ifPresent(EventStock::releaseSeat);
                        stockRepository.flush(); // le conflit de version eclate ici
                    } else if (ticket.isPaid()) {
                        log.warn("Payment failed for ticket {} which is already paid, ignored", ticketId);
                    }
                    // deja CANCELLED : message en double, on ne rend pas la place deux fois
                }, () -> log.warn("Payment failed for unknown ticket {}, ignored", ticketId))));
    }

    private void retryOnConflict(Runnable action) {
        for (int attempt = 1; ; attempt++) {
            try {
                action.run();
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                backoff(attempt);
            }
        }
    }
}