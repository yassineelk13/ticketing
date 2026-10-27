package com.billetterie.event_service.cache;

import com.billetterie.event_service.dto.EventResponse;
import com.billetterie.event_service.event.Event;
import com.billetterie.event_service.event.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventCacheTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private EventCache cache;

    @BeforeEach
    void setUp() {
        cache = new EventCache(redis, jsonMapper, 600);
    }

    private static EventResponse sample() {
        Event event = new Event("Rap marocain", "Soiree", EventType.CONCERT, "Agadir", "Theatre de Verdure",
                Instant.parse("2026-12-20T20:00:00Z"), 2, "org-1", Map.of("artist", "Stormy"));
        ReflectionTestUtils.setField(event, "id", "evt-1");
        return EventResponse.from(event);
    }

    @Test
    void get_whenNothingIsCached_returnsEmpty() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("event:evt-1")).thenReturn(null);

        assertThat(cache.get("evt-1")).isEmpty();
    }

    @Test
    void get_returnsTheCachedEvent() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("event:evt-1")).thenReturn(jsonMapper.writeValueAsString(sample()));

        assertThat(cache.get("evt-1")).contains(sample());
    }

    @Test
    void put_storesTheJsonUnderTheEventKeyWithTheConfiguredTtl() {
        when(redis.opsForValue()).thenReturn(values);

        cache.put("evt-1", sample());

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("event:evt-1"), json.capture(), eq(Duration.ofSeconds(600)));
        assertThat(json.getValue()).contains("evt-1").contains("Rap marocain");
    }

    @Test
    void evict_deletesTheKey() {
        cache.evict("evt-1");

        verify(redis).delete("event:evt-1");
    }

    @Test
    void whenRedisIsDown_readsReturnEmpty_andWritesAndEvictionsDoNotFail() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("redis is down"));
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("redis is down"));

        assertThat(cache.get("evt-1")).isEmpty();
        assertThatCode(() -> cache.put("evt-1", sample())).doesNotThrowAnyException();
        assertThatCode(() -> cache.evict("evt-1")).doesNotThrowAnyException();
    }

    @Test
    void corruptedCacheContent_isTreatedAsAMiss() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("event:evt-1")).thenReturn("ceci n'est pas du json");

        assertThat(cache.get("evt-1")).isEmpty();
    }
}