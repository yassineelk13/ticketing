package com.billetterie.ticket_service.client;

import com.billetterie.ticket_service.exception.EventNotFoundException;
import com.billetterie.ticket_service.exception.EventServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Component
public class EventClient {

    private final RestClient restClient;

    public EventClient(@Value("${app.event-service.url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public EventInfo getEvent(String eventId) {
        try {
            return restClient.get()
                    .uri("/events/{id}", eventId)
                    .retrieve()
                    .body(EventInfo.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new EventNotFoundException(eventId);
        } catch (RestClientException e) {
            throw new EventServiceUnavailableException(e);
        }
    }
}