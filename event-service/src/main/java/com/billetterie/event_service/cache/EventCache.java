package com.billetterie.event_service.cache;

import com.billetterie.event_service.dto.EventResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

@Component
public class EventCache {

    private static final Logger log = LoggerFactory.getLogger(EventCache.class);
    private static final String PREFIX = "event:";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final Duration ttl;

    public EventCache(StringRedisTemplate redis,
                      JsonMapper jsonMapper,
                      @Value("${app.cache.event-ttl-seconds:600}") long ttlSeconds) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<EventResponse> get(String eventId) {
        try {
            String json = redis.opsForValue().get(key(eventId));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(jsonMapper.readValue(json, EventResponse.class));
        } catch (RuntimeException e) {
            log.warn("Event cache read failed for {}: {}", eventId, e.toString());
            return Optional.empty();
        }
    }

    public void put(String eventId, EventResponse event) {
        try {
            redis.opsForValue().set(key(eventId), jsonMapper.writeValueAsString(event), ttl);
        } catch (RuntimeException e) {
            log.warn("Event cache write failed for {}: {}", eventId, e.toString());
        }
    }

    public void evict(String eventId) {
        try {
            redis.delete(key(eventId));
        } catch (RuntimeException e) {
            log.warn("Event cache eviction failed for {}: {}", eventId, e.toString());
        }
    }

    private static String key(String eventId) {
        return PREFIX + eventId;
    }
}