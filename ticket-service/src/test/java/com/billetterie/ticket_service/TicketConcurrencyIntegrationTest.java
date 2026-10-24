package com.billetterie.ticket_service;

import com.billetterie.ticket_service.client.EventClient;
import com.billetterie.ticket_service.client.EventInfo;
import com.billetterie.ticket_service.exception.SoldOutException;
import com.billetterie.ticket_service.ticket.EventStock;
import com.billetterie.ticket_service.ticket.EventStockRepository;
import com.billetterie.ticket_service.ticket.TicketRepository;
import com.billetterie.ticket_service.ticket.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "app.jwt.secret=concurrency-test-secret-at-least-32-bytes-long!",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=30"
})
@Import(TestcontainersConfiguration.class)
class TicketConcurrencyIntegrationTest {

    private static final String EVENT_ID = "concurrent-event";
    private static final String NEW_EVENT_ID = "brand-new-event";
    private static final int SEATS = 5;
    private static final int BUYERS = 50;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private EventStockRepository stockRepository;

    @MockitoBean
    private EventClient eventClient;

    private record Outcome(int succeeded, int soldOut, List<String> unexpectedErrors) {
    }

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        stockRepository.deleteAll();
        // cet evenement a deja son stock ; NEW_EVENT_ID, lui, n'en a pas
        stockRepository.save(new EventStock(EVENT_ID, SEATS));
    }

    private Outcome runConcurrentReservations(String eventId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(BUYERS);
        CountDownLatch ready = new CountDownLatch(BUYERS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < BUYERS; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await(); // tout le monde attend le meme signal de depart
                try {
                    ticketService.reserve(eventId, UUID.randomUUID());
                    succeeded.incrementAndGet();
                } catch (SoldOutException e) {
                    soldOut.incrementAndGet();
                } catch (Exception e) {
                    errors.add(e.getClass().getSimpleName());
                }
                return null;
            }));
        }

        ready.await();
        go.countDown(); // depart simultane
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        return new Outcome(succeeded.get(), soldOut.get(), List.copyOf(errors));
    }

    @Test
    void concurrentReservations_neverSellMoreSeatsThanAvailable() throws Exception {
        Outcome outcome = runConcurrentReservations(EVENT_ID);

        assertThat(outcome.unexpectedErrors()).as("unexpected errors").isEmpty();
        assertThat(ticketRepository.count()).as("tickets actually sold").isEqualTo(SEATS);
        assertThat(stockRepository.findById(EVENT_ID).orElseThrow().getAvailableSeats())
                .as("seats left in stock").isZero();
        assertThat(outcome.succeeded()).as("successful reservations").isEqualTo(SEATS);
        assertThat(outcome.soldOut()).as("sold-out answers").isEqualTo(BUYERS - SEATS);
    }

    @Test
    void firstReservationsOfAnEventNeverSeenBefore_createTheStockOnlyOnce() throws Exception {
        when(eventClient.getEvent(NEW_EVENT_ID)).thenReturn(new EventInfo(NEW_EVENT_ID, SEATS));

        Outcome outcome = runConcurrentReservations(NEW_EVENT_ID);

        assertThat(outcome.unexpectedErrors()).as("unexpected errors").isEmpty();
        assertThat(ticketRepository.count()).as("tickets actually sold").isEqualTo(SEATS);
        assertThat(stockRepository.findById(NEW_EVENT_ID).orElseThrow().getAvailableSeats())
                .as("seats left in stock").isZero();
        assertThat(outcome.succeeded()).as("successful reservations").isEqualTo(SEATS);
        assertThat(outcome.soldOut()).as("sold-out answers").isEqualTo(BUYERS - SEATS);
    }
}