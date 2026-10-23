package com.billetterie.event_service.event;

import com.billetterie.event_service.dto.EventRequest;
import com.billetterie.event_service.exception.EventNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    private static EventRequest request(String title) {
        return new EventRequest(title, "desc", EventType.CONCERT, " Agadir ", " Theatre ",
                Instant.parse("2030-01-01T20:00:00Z"), 100, Map.of("artist", "X"));
    }

    @Test
    void create_setsOrganizerFromArgumentAndTrimsInput() {
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        Event created = eventService.create(request("  Concert  "), "organizer-1");

        assertThat(created.getOrganizerId()).isEqualTo("organizer-1");
        assertThat(created.getTitle()).isEqualTo("Concert");
        assertThat(created.getCity()).isEqualTo("Agadir");
        assertThat(created.getVenue()).isEqualTo("Theatre");
    }

    @Test
    void getById_throwsWhenEventDoesNotExist() {
        when(eventRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getById("nope"))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void update_succeedsForTheOwner() {
        Event existing = new Event("Old", "d", EventType.OTHER, "Rabat", "V",
                Instant.parse("2030-01-01T20:00:00Z"), 10, "owner", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(existing));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        Event updated = eventService.update("e1", request("New"), "owner");

        assertThat(updated.getTitle()).isEqualTo("New");
        assertThat(updated.getOrganizerId()).isEqualTo("owner");
    }

    @Test
    void update_isForbiddenForAnotherOrganizer() {
        Event existing = new Event("Old", "d", EventType.OTHER, "Rabat", "V",
                Instant.parse("2030-01-01T20:00:00Z"), 10, "owner", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> eventService.update("e1", request("Hack"), "intruder"))
                .isInstanceOf(AccessDeniedException.class);

        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void search_withoutFilters_returnsEverything() {
        when(eventRepository.findAll()).thenReturn(List.of());

        assertThat(eventService.search(null, null)).isEmpty();
        verify(eventRepository).findAll();
    }

    @Test
    void search_withCityOnly_usesCityQuery() {
        eventService.search(" Agadir ", null);

        verify(eventRepository).findByCityIgnoreCase("Agadir");
    }

    @Test
    void search_withCityAndDate_usesCombinedQuery() {
        eventService.search("Agadir", LocalDate.of(2030, 1, 1));

        verify(eventRepository).findByCityIgnoreCaseAndStartDateBetween(
                org.mockito.ArgumentMatchers.eq("Agadir"),
                org.mockito.ArgumentMatchers.eq(Instant.parse("2030-01-01T00:00:00Z")),
                org.mockito.ArgumentMatchers.eq(Instant.parse("2030-01-02T00:00:00Z")));
    }
}