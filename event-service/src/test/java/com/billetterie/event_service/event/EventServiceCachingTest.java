package com.billetterie.event_service.event;

import com.billetterie.event_service.cache.EventCache;
import com.billetterie.event_service.dto.EventRequest;
import com.billetterie.event_service.dto.EventResponse;
import com.billetterie.event_service.exception.EventNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceCachingTest {

    @Mock
    private EventRepository repository;

    @Mock
    private EventCache cache;

    private EventService service;

    @BeforeEach
    void setUp() {
        service = new EventService(repository, cache);
    }

    private static Event storedEvent() {
        Event event = new Event("Rap marocain", "Soiree", EventType.CONCERT, "Agadir", "Theatre de Verdure",
                Instant.parse("2026-12-20T20:00:00Z"), 2, "org-1", Map.of("artist", "Stormy"));
        ReflectionTestUtils.setField(event, "id", "evt-1");
        return event;
    }

    private static EventRequest request() {
        EventRequest request = mock(EventRequest.class);
        lenient().when(request.title()).thenReturn("New title");
        lenient().when(request.description()).thenReturn("New description");
        lenient().when(request.type()).thenReturn(EventType.CONCERT);
        lenient().when(request.city()).thenReturn("Rabat");
        lenient().when(request.venue()).thenReturn("Salle");
        lenient().when(request.startDate()).thenReturn(Instant.parse("2027-01-01T20:00:00Z"));
        lenient().when(request.totalSeats()).thenReturn(10);
        lenient().when(request.attributes()).thenReturn(Map.of());
        return request;
    }

    @Test
    void getById_onAMiss_readsTheDatabaseAndFillsTheCache() {
        Event event = storedEvent();
        when(cache.get("evt-1")).thenReturn(Optional.empty());
        when(repository.findById("evt-1")).thenReturn(Optional.of(event));

        EventResponse response = service.getById("evt-1");

        assertThat(response).isEqualTo(EventResponse.from(event));
        verify(cache).put("evt-1", response);
    }

    @Test
    void getById_onAHit_doesNotTouchTheDatabase() {
        EventResponse cached = EventResponse.from(storedEvent());
        when(cache.get("evt-1")).thenReturn(Optional.of(cached));

        assertThat(service.getById("evt-1")).isSameAs(cached);

        verifyNoInteractions(repository);
    }

    @Test
    void getById_unknownEvent_throwsAndCachesNothing() {
        when(cache.get("ghost")).thenReturn(Optional.empty());
        when(repository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("ghost")).isInstanceOf(EventNotFoundException.class);

        verify(cache, never()).put(any(), any());
    }

    @Test
    void update_savesFirst_thenEvictsTheCache() {
        Event event = storedEvent();
        when(repository.findById("evt-1")).thenReturn(Optional.of(event));
        when(repository.save(event)).thenReturn(event);

        service.update("evt-1", request(), "org-1");

        InOrder order = inOrder(repository, cache);
        order.verify(repository).save(event);
        order.verify(cache).evict("evt-1");
    }

    @Test
    void update_byAnotherOrganizer_isRefused_andTheCacheIsKept() {
        when(repository.findById("evt-1")).thenReturn(Optional.of(storedEvent()));

        assertThatThrownBy(() -> service.update("evt-1", request(), "org-2"))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any(Event.class));
        verify(cache, never()).evict(any());
    }

    @Test
    void update_readsTheDatabase_neverTheCache() {
        Event event = storedEvent();
        when(repository.findById("evt-1")).thenReturn(Optional.of(event));
        when(repository.save(event)).thenReturn(event);

        service.update("evt-1", request(), "org-1");

        verify(cache, never()).get(any());
    }
}