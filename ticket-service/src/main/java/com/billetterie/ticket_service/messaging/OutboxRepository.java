package com.billetterie.ticket_service.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    List<OutboxMessage> findTop50BySentAtIsNullOrderByCreatedAtAsc();

    @Modifying
    @Transactional
    @Query("update OutboxMessage m set m.sentAt = :sentAt where m.id = :id")
    int markSent(@Param("id") UUID id, @Param("sentAt") Instant sentAt);
}