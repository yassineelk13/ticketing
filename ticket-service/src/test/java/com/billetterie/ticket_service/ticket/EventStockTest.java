package com.billetterie.ticket_service.ticket;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventStockTest {

    @Test
    void newStock_startsFull() {
        EventStock stock = new EventStock("event-1", 3);

        assertThat(stock.getTotalSeats()).isEqualTo(3);
        assertThat(stock.getAvailableSeats()).isEqualTo(3);
        assertThat(stock.hasAvailableSeats()).isTrue();
    }

    @Test
    void reserveSeat_decrementsAvailableSeats() {
        EventStock stock = new EventStock("event-1", 3);

        stock.reserveSeat();

        assertThat(stock.getAvailableSeats()).isEqualTo(2);
    }

    @Test
    void reserveSeat_whenNoSeatLeft_throws() {
        EventStock stock = new EventStock("event-1", 1);
        stock.reserveSeat();

        assertThat(stock.hasAvailableSeats()).isFalse();
        assertThatThrownBy(stock::reserveSeat).isInstanceOf(IllegalStateException.class);
        assertThat(stock.getAvailableSeats()).isZero();
    }

    @Test
    void releaseSeat_neverGoesAboveTotal() {
        EventStock stock = new EventStock("event-1", 2);

        stock.releaseSeat();

        assertThat(stock.getAvailableSeats()).isEqualTo(2);
    }
}