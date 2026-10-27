package com.billetterie.event_service.cache;

import com.billetterie.event_service.dto.EventResponse;
import com.billetterie.event_service.dto.EventRequest;
import com.billetterie.event_service.event.Event;
import com.billetterie.event_service.event.EventRepository;
import com.billetterie.event_service.event.EventService;
import com.billetterie.event_service.event.EventType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
class EventCacheRedisIntegrationTest {

    @Container
    @SuppressWarnings({"rawtypes", "unchecked"})
    static final GenericContainer redis = new GenericContainer("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate template;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void cleanRedis() {
        Set<String> keys = template.keys("event:*");
        if (keys != null && !keys.isEmpty()) {
            template.delete(keys);
        }
    }

    // ---------- helpers ----------

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

    // ---------- tests ----------

    @Test
    void anEventSurvivesARealRoundTripThroughRedis() {
        EventCache cache = new EventCache(template, jsonMapper, 600);
        EventResponse original = EventResponse.from(storedEvent());

        cache.put("evt-1", original);

        assertThat(cache.get("evt-1")).contains(original);
    }

    @Test
    void theKeyGetsAnExpirationTime() {
        EventCache cache = new EventCache(template, jsonMapper, 600);

        cache.put("evt-1", EventResponse.from(storedEvent()));

        Long seconds = template.getExpire("event:evt-1");
        assertThat(seconds).isNotNull().isBetween(1L, 600L);
    }

    @Test
    void theEntryDisappearsAfterItsTtl() throws InterruptedException {
        EventCache cache = new EventCache(template, jsonMapper, 1);
        cache.put("evt-1", EventResponse.from(storedEvent()));
        assertThat(cache.get("evt-1")).isPresent();

        long deadline = System.currentTimeMillis() + 10_000;
        while (cache.get("evt-1").isPresent() && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }

        assertThat(cache.get("evt-1")).isEmpty();
    }

    @Test
    void evictRemovesTheEntry() {
        EventCache cache = new EventCache(template, jsonMapper, 600);
        cache.put("evt-1", EventResponse.from(storedEvent()));

        cache.evict("evt-1");

        assertThat(cache.get("evt-1")).isEmpty();
    }

    @Test
    void theServiceReadsTheDatabaseOnlyOnce_untilTheEventIsUpdated() {
        EventCache cache = new EventCache(template, jsonMapper, 600);
        EventRepository repository = mock(EventRepository.class);
        Event stored = storedEvent();
        when(repository.findById("evt-1")).thenReturn(Optional.of(stored));
        when(repository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
        EventService service = new EventService(repository, cache);

        EventResponse before = service.getById("evt-1");
        service.getById("evt-1");
        service.getById("evt-1");
        verify(repository, times(1)).findById("evt-1");     // 3 lectures, 1 seul acces a la base

        service.update("evt-1", request(), "org-1");         // lit la base (2e acces) puis vide le cache
        EventResponse after = service.getById("evt-1");      // cache vide : relit la base (3e acces)

        verify(repository, times(3)).findById("evt-1");
        assertThat(after).isNotEqualTo(before);              // la nouvelle version, pas l'ancienne
        assertThat(after).isEqualTo(EventResponse.from(stored));
    }
}