package com.billetterie.ticket_service;

import com.billetterie.ticket_service.client.EventClient;
import com.billetterie.ticket_service.client.EventInfo;
import com.billetterie.ticket_service.dto.TicketResponse;
import com.billetterie.ticket_service.exception.EventNotFoundException;
import com.billetterie.ticket_service.exception.EventServiceUnavailableException;
import com.billetterie.ticket_service.ticket.EventStockRepository;
import com.billetterie.ticket_service.ticket.TicketRepository;
import com.billetterie.ticket_service.ticket.TicketStatus;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.jwt.secret=" + TicketFlowIntegrationTest.SECRET,
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        })
@Import(TestcontainersConfiguration.class)
class TicketFlowIntegrationTest {

    static final String SECRET = "integration-test-secret-at-least-32-bytes-long!";
    private static final String EVENT_ID = "event-1";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private EventStockRepository stockRepository;

    @MockitoBean
    private EventClient eventClient;

    private RestClient client;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        stockRepository.deleteAll();
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();

        when(eventClient.getEvent(EVENT_ID)).thenReturn(new EventInfo(EVENT_ID, 2));
        when(eventClient.getEvent("ghost")).thenThrow(new EventNotFoundException("ghost"));
        when(eventClient.getEvent("down"))
                .thenThrow(new EventServiceUnavailableException(new RuntimeException("boom")));
    }

    // ---------- helpers ----------

    private String tokenFor(UUID userId) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("role", "USER")
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String path, Object body,
                                           String token, Class<T> type) {
        RestClient.RequestBodySpec request = client.method(method)
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.body(body);
        }
        return request.retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { })
                .toEntity(type);
    }

    private ResponseEntity<String> reserve(UUID user, String eventId) {
        return exchange(HttpMethod.POST, "/tickets/reserve", Map.of("eventId", eventId),
                tokenFor(user), String.class);
    }

    private UUID reserveAndGetId(UUID user, String eventId) {
        ResponseEntity<TicketResponse> response = exchange(HttpMethod.POST, "/tickets/reserve",
                Map.of("eventId", eventId), tokenFor(user), TicketResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return response.getBody().id();
    }

    private int availableSeats() {
        return stockRepository.findById(EVENT_ID).orElseThrow().getAvailableSeats();
    }

    // ---------- tests ----------

    @Test
    void reserve_withoutToken_returns401() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/tickets/reserve",
                Map.of("eventId", EVENT_ID), null, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void reserve_untilSoldOut_returns409OnTheLastAttempt() {
        assertThat(reserve(alice, EVENT_ID).getStatusCode().value()).isEqualTo(201);
        assertThat(reserve(bob, EVENT_ID).getStatusCode().value()).isEqualTo(201);
        assertThat(reserve(alice, EVENT_ID).getStatusCode().value()).isEqualTo(409);

        assertThat(availableSeats()).isZero();
        assertThat(ticketRepository.count()).isEqualTo(2);
    }

    @Test
    void cancel_releasesTheSeatSoItCanBeReservedAgain() {
        UUID firstTicketId = reserveAndGetId(alice, EVENT_ID);
        reserve(bob, EVENT_ID);
        assertThat(reserve(alice, EVENT_ID).getStatusCode().value()).isEqualTo(409);

        ResponseEntity<String> cancel = exchange(HttpMethod.DELETE, "/tickets/" + firstTicketId,
                null, tokenFor(alice), String.class);

        assertThat(cancel.getStatusCode().value()).isEqualTo(204);
        assertThat(ticketRepository.findById(firstTicketId).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.CANCELLED);
        assertThat(availableSeats()).isEqualTo(1);
        assertThat(reserve(alice, EVENT_ID).getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void cancel_ticketOfAnotherUser_returns404AndKeepsTheTicket() {
        UUID aliceTicketId = reserveAndGetId(alice, EVENT_ID);

        ResponseEntity<String> response = exchange(HttpMethod.DELETE, "/tickets/" + aliceTicketId,
                null, tokenFor(bob), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(ticketRepository.findById(aliceTicketId).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.RESERVED);
        assertThat(availableSeats()).isEqualTo(1);
    }

    @Test
    void myTickets_returnsOnlyTheTicketsOfTheCaller() {
        reserve(alice, EVENT_ID);
        reserve(bob, EVENT_ID);

        ResponseEntity<TicketResponse[]> response = exchange(HttpMethod.GET, "/tickets/my-tickets",
                null, tokenFor(alice), TicketResponse[].class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].eventId()).isEqualTo(EVENT_ID);
    }

    @Test
    void reserve_unknownEvent_returns404() {
        assertThat(reserve(alice, "ghost").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void reserve_whenEventServiceIsDown_returns503() {
        assertThat(reserve(alice, "down").getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void reserve_withBlankEventId_returns400() {
        assertThat(reserve(alice, "").getStatusCode().value()).isEqualTo(400);
    }
}