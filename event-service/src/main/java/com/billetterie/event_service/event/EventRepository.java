package com.billetterie.event_service.event;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface EventRepository extends MongoRepository<Event, String> {

    List<Event> findByCityIgnoreCase(String city);

    List<Event> findByStartDateBetween(Instant from, Instant to);

    List<Event> findByCityIgnoreCaseAndStartDateBetween(String city, Instant from, Instant to);
}