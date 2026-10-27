package com.billetterie.event_service.event;

import com.billetterie.event_service.dto.EventRequest;
import com.billetterie.event_service.dto.EventResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody EventRequest request) {
        Event event = eventService.create(request, jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(EventResponse.from(event));
    }

    @GetMapping("/{id}")
    public EventResponse getById(@PathVariable String id) {
        return eventService.getById(id);
    }

    @GetMapping
    public List<EventResponse> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return eventService.search(city, date).stream().map(EventResponse::from).toList();
    }

    @PutMapping("/{id}")
    public EventResponse update(@PathVariable String id,
                                @AuthenticationPrincipal Jwt jwt,
                                @Valid @RequestBody EventRequest request) {
        return EventResponse.from(eventService.update(id, request, jwt.getSubject()));
    }
}