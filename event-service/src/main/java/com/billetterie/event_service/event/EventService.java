package com.billetterie.event_service.event;

import com.billetterie.event_service.dto.EventRequest;
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

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
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

    public Event getById(String id) {
        return eventRepository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
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
        Event event = getById(id);
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
        return eventRepository.save(event);
    }
}