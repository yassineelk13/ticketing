package com.billetterie.ticket_service.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EventStockRepository extends JpaRepository<EventStock, String> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO event_stocks (event_id, total_seats, available_seats, version)
            VALUES (:eventId, :totalSeats, :totalSeats, 0)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("totalSeats") int totalSeats);
}