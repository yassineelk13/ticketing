package com.billetterie.ticket_service.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventStockRepository extends JpaRepository<EventStock, String> {
}