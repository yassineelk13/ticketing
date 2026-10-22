package com.billetterie.user_service;


import com.billetterie.user_service.config.JwtConfig;
import com.billetterie.user_service.auth.TokenService;
import com.billetterie.user_service.dto.TokenResponse;
import com.billetterie.user_service.dto.UserResponse;
import com.billetterie.user_service.user.Role;
import com.billetterie.user_service.user.User;
import com.billetterie.user_service.user.UserRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

    private static final String EMAIL = "yassine@gmail.com";
    private static final String PASSWORD = "password123";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UserRepository userRepository;

    private RestClient client;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    // ---------- helpers ----------

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
        // on ne veut pas d'exception sur 4xx/5xx : on veut examiner le statut
        return request.retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { })
                .toEntity(type);
    }

    private ResponseEntity<String> register(String email, String password) {
        return exchange(HttpMethod.POST, "/users/register",
                Map.of("email", email, "password", password,
                        "firstName", "Yassine", "lastName", "Test", "role", "USER"),
                null, String.class);
    }

    private String loginAndGetToken(String email, String password) {
        ResponseEntity<TokenResponse> response = exchange(HttpMethod.POST, "/users/login",
                Map.of("email", email, "password", password), null, TokenResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody().accessToken();
    }

    // ---------- tests ----------

    @Test
    void fullFlow_register_login_me_update() {
        assertThat(register(EMAIL, PASSWORD).getStatusCode().value()).isEqualTo(201);

        String token = loginAndGetToken(EMAIL, PASSWORD);

        ResponseEntity<UserResponse> me = exchange(HttpMethod.GET, "/users/me", null, token, UserResponse.class);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getBody().email()).isEqualTo(EMAIL);
        assertThat(me.getBody().role()).isEqualTo(Role.USER);

        ResponseEntity<UserResponse> updated = exchange(HttpMethod.PUT, "/users/me",
                Map.of("firstName", "Nouveau", "lastName", "Nom"), token, UserResponse.class);
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat(updated.getBody().firstName()).isEqualTo("Nouveau");

        ResponseEntity<UserResponse> meAgain = exchange(HttpMethod.GET, "/users/me", null, token, UserResponse.class);
        assertThat(meAgain.getBody().firstName()).isEqualTo("Nouveau");
        assertThat(meAgain.getBody().lastName()).isEqualTo("Nom");
    }

    @Test
    void register_neverReturnsOrStoresThePlainPassword() {
        ResponseEntity<String> response = register(EMAIL, PASSWORD);

        assertThat(response.getBody()).doesNotContain(PASSWORD).doesNotContain("password");

        User stored = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void register_withSameEmailInDifferentCase_returns409() {
        assertThat(register("Yassine@Gmail.com", PASSWORD).getStatusCode().value()).isEqualTo(201);

        assertThat(register(EMAIL, PASSWORD).getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void register_withInvalidPayload_returns400() {
        ResponseEntity<String> response = register("not-an-email", "123");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("errors");
    }

    @Test
    void login_withWrongPasswordOrUnknownEmail_returnsSame401() {
        register(EMAIL, PASSWORD);

        ResponseEntity<String> wrongPassword = exchange(HttpMethod.POST, "/users/login",
                Map.of("email", EMAIL, "password", "wrong-password"), null, String.class);
        ResponseEntity<String> unknownEmail = exchange(HttpMethod.POST, "/users/login",
                Map.of("email", "ghost@gmail.com", "password", PASSWORD), null, String.class);

        assertThat(wrongPassword.getStatusCode().value()).isEqualTo(401);
        assertThat(unknownEmail.getStatusCode().value()).isEqualTo(401);
        assertThat(wrongPassword.getBody()).contains("Invalid email or password");
        assertThat(unknownEmail.getBody()).contains("Invalid email or password");
    }

    @Test
    void me_withoutToken_returns401() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/users/me", null, null, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void me_withGarbageToken_returns401() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/users/me", null, "not.a.jwt", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void me_withTokenSignedByAttacker_returns401() {
        User fake = new User(EMAIL, "x", "A", "B", Role.ORGANIZER);
        ReflectionTestUtils.setField(fake, "id", UUID.randomUUID());
        String forged = new TokenService(
                new JwtConfig("attacker-secret-attacker-secret-1234").jwtEncoder(), 60).generate(fake);

        ResponseEntity<String> response = exchange(HttpMethod.GET, "/users/me", null, forged, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void updateProfile_cannotChangeRole() {
        register(EMAIL, PASSWORD);
        String token = loginAndGetToken(EMAIL, PASSWORD);

        ResponseEntity<String> response = exchange(HttpMethod.PUT, "/users/me",
                Map.of("firstName", "Hacker", "lastName", "Test", "role", "ORGANIZER"),
                token, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getRole()).isEqualTo(Role.USER);
    }
}