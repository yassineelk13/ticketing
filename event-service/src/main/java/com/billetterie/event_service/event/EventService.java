package com.billetterie.event_service.event;

import com.billetterie.event_service.cache.EventCache;
import com.billetterie.event_service.dto.EventRequest;
import com.billetterie.event_service.dto.EventResponse;
import com.billetterie.event_service.exception.EventNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventCache cache;

    public EventService(EventRepository eventRepository, EventCache cache) {
        this.eventRepository = eventRepository;
        this.cache = cache;
    }

    public Event create(EventRequest request, String organizerId) {
        Event event = new Event(
                request.title().trim(),
                request.description(),
                request.type(),
                request.city().trim(),
                request.venue().trim(),
                request.startDate(),
                request.totalSeats(),
                organizerId,
                request.attributes()
        );
        return eventRepository.save(event);
    }

    public EventResponse getById(String id) {
        return cache.get(id).orElseGet(() -> {
            EventResponse response = EventResponse.from(findOrThrow(id));
            cache.put(id, response);
            return response;
        });
    }

    public List<Event> search(String city, LocalDate date) {
        Instant from = null;
        Instant to = null;
        if (date != null) {
            from = date.atStartOfDay().toInstant(ZoneOffset.UTC);
            to = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        }

        if (city != null && date != null) {
            return eventRepository.findByCityIgnoreCaseAndStartDateBetween(city.trim(), from, to);
        }
        if (city != null) {
            return eventRepository.findByCityIgnoreCase(city.trim());
        }
        if (date != null) {
            return eventRepository.findByStartDateBetween(from, to);
        }
        return eventRepository.findAll();
    }

    public Event update(String id, EventRequest request, String organizerId) {
        Event event = findOrThrow(id);   // toujours la base : jamais une copie du cache
        if (!event.getOrganizerId().equals(organizerId)) {
            throw new AccessDeniedException("You can only modify your own events");
        }
        event.update(
                request.title().trim(),
                request.description(),
                request.type(),
                request.city().trim(),
                request.venue().trim(),
                request.startDate(),
                request.totalSeats(),
                request.attributes()
        );
        Event saved = eventRepository.save(event);
        cache.evict(id);                 // apres la sauvegarde : la prochaine lecture rechargera la base
        return saved;
    }

    private Event findOrThrow(String id) {
        return eventRepository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }
}