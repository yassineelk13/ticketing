package com.billetterie.event_service;

import com.billetterie.event_service.event.EventRepository;
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
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.client.RestClient;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=integration-test-secret-at-least-32-bytes!")
@Import(TestcontainersConfiguration.class)
class EventFlowIntegrationTest {

    private static final String SECRET = "integration-test-secret-at-least-32-bytes!";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private EventRepository eventRepository;

    private RestClient client;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    private String token(String subject, String role) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("billetterie-user-service")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("role", role)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, Object body, String token) {
        RestClient.RequestBodySpec request = client.method(method).uri(path)
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.body(body);
        }
        return request.retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { })
                .toEntity(String.class);
    }

    private Map<String, Object> validEvent(String city) {
        return Map.of("title", "Concert Gnawa", "description", "Soiree", "type", "CONCERT",
                "city", city, "venue", "Theatre", "startDate", "2099-12-20T20:00:00Z",
                "totalSeats", 200, "attributes", Map.of("artist", "Maalem"));
    }

    private String createAndGetId(String organizerToken) {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/events", validEvent("Agadir"), organizerToken);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        String body = response.getBody();
        int start = body.indexOf("\"id\":\"") + 6;
        return body.substring(start, body.indexOf('"', start));
    }

    @Test
    void organizer_canCreateEvent_andAnyoneCanReadIt() {
        String organizer = token(UUID.randomUUID().toString(), "ORGANIZER");
        String id = createAndGetId(organizer);

        ResponseEntity<String> read = exchange(HttpMethod.GET, "/events/" + id, null, null);
        assertThat(read.getStatusCode().value()).isEqualTo(200);
        assertThat(read.getBody()).contains("Concert Gnawa").contains("Maalem");
    }

    @Test
    void search_byCity_isCaseInsensitive() {
        createAndGetId(token(UUID.randomUUID().toString(), "ORGANIZER"));

        ResponseEntity<String> response = exchange(HttpMethod.GET, "/events?city=agadir", null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("Concert Gnawa");
    }

    @Test
    void create_withoutToken_returns401() {
        assertThat(exchange(HttpMethod.POST, "/events", validEvent("Agadir"), null)
                .getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void create_withUserRole_returns403() {
        String user = token(UUID.randomUUID().toString(), "USER");

        assertThat(exchange(HttpMethod.POST, "/events", validEvent("Agadir"), user)
                .getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void create_withInvalidPayload_returns400() {
        String organizer = token(UUID.randomUUID().toString(), "ORGANIZER");

        ResponseEntity<String> response = exchange(HttpMethod.POST, "/events",
                Map.of("title", "", "type", "CONCERT"), organizer);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("errors");
    }

    @Test
    void update_byAnotherOrganizer_returns403() {
        String id = createAndGetId(token(UUID.randomUUID().toString(), "ORGANIZER"));
        String intruder = token(UUID.randomUUID().toString(), "ORGANIZER");

        assertThat(exchange(HttpMethod.PUT, "/events/" + id, validEvent("Rabat"), intruder)
                .getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void update_byOwner_changesTheEvent() {
        String owner = token(UUID.randomUUID().toString(), "ORGANIZER");
        String id = createAndGetId(owner);

        ResponseEntity<String> response = exchange(HttpMethod.PUT, "/events/" + id, validEvent("Rabat"), owner);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("Rabat");
    }

    @Test
    void getUnknownEvent_returns404() {
        assertThat(exchange(HttpMethod.GET, "/events/does-not-exist", null, null)
                .getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void token_signedWithAnotherSecret_returns401() {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec("attacker-secret-attacker-secret-1234".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        String forged = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject("x").issuedAt(now).expiresAt(now.plusSeconds(60))
                        .claim("role", "ORGANIZER").build())).getTokenValue();

        assertThat(exchange(HttpMethod.POST, "/events", validEvent("Agadir"), forged)
                .getStatusCode().value()).isEqualTo(401);
    }
}