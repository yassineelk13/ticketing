package com.billetterie.ticket_service.messaging;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Component
public class OutboxWriter {

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    public OutboxWriter(OutboxRepository outboxRepository, JsonMapper jsonMapper) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void add(String topic, UUID key, Object event) {
        String payload = jsonMapper.writeValueAsString(event);
        outboxRepository.save(new OutboxMessage(topic, key.toString(), payload));
    }
}