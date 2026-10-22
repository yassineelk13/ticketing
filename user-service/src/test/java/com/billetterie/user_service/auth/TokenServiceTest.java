package com.billetterie.user_service.auth;

import com.billetterie.user_service.config.JwtConfig;
import com.billetterie.user_service.user.Role;
import com.billetterie.user_service.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final String SECRET = "unit-test-secret-with-at-least-32-bytes!";

    private final JwtConfig jwtConfig = new JwtConfig(SECRET);
    private final TokenService tokenService = new TokenService(jwtConfig.jwtEncoder(), 60);

    private static User userWithId(UUID id) {
        User user = new User("yassine@gmail.com", "hash", "Yassine", "Test", Role.ORGANIZER);
        // l'id est normalement genere par la base, on le force pour le test
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void generate_createsSignedTokenWithExpectedClaims() {
        UUID id = UUID.randomUUID();

        String token = tokenService.generate(userWithId(id));
        Jwt jwt = jwtConfig.jwtDecoder().decode(token);

        assertThat(jwt.getSubject()).isEqualTo(id.toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("yassine@gmail.com");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ORGANIZER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("billetterie-user-service");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void decode_rejectsTokenSignedWithAnotherSecret() {
        String token = tokenService.generate(userWithId(UUID.randomUUID()));
        JwtConfig otherConfig = new JwtConfig("another-secret-another-secret-123456");

        assertThatThrownBy(() -> otherConfig.jwtDecoder().decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void decode_rejectsExpiredToken() {
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("billetterie-user-service")
                .subject(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofHours(1)))
                .build();
        String token = jwtConfig.jwtEncoder()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        assertThatThrownBy(() -> jwtConfig.jwtDecoder().decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void jwtConfig_refusesSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtConfig("too-short"))
                .isInstanceOf(IllegalStateException.class);
    }
}