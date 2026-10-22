package com.billetterie.user_service.auth;

import com.billetterie.user_service.dto.LoginRequest;
import com.billetterie.user_service.dto.TokenResponse;
import com.billetterie.user_service.exception.InvalidCredentialsException;
import com.billetterie.user_service.user.Role;
import com.billetterie.user_service.user.User;
import com.billetterie.user_service.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // le constructeur d'AuthService calcule un "faux hash" avec encode(...)
        when(passwordEncoder.encode("dummy-password")).thenReturn("dummy-hash");
        authService = new AuthService(userRepository, passwordEncoder, tokenService);
    }

    @Test
    void login_returnsTokenWhenCredentialsAreValid() {
        User user = new User("yassine@gmail.com", "stored-hash", "Yassine", "Test", Role.USER);
        when(userRepository.findByEmail("yassine@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "stored-hash")).thenReturn(true);
        when(tokenService.generate(user)).thenReturn("jwt-token");
        when(tokenService.expiresInSeconds()).thenReturn(3600L);

        TokenResponse response = authService.login(new LoginRequest("  Yassine@Gmail.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(3600L);
    }

    @Test
    void login_failsWhenPasswordIsWrong() {
        User user = new User("yassine@gmail.com", "stored-hash", "Yassine", "Test", Role.USER);
        when(userRepository.findByEmail("yassine@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("yassine@gmail.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(tokenService, never()).generate(any(User.class));
    }

    @Test
    void login_failsForUnknownEmailButStillComparesAgainstDummyHash() {
        when(userRepository.findByEmail("ghost@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@gmail.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);

        // protection contre l'attaque par mesure du temps de reponse
        verify(passwordEncoder).matches("password123", "dummy-hash");
        verify(tokenService, never()).generate(any(User.class));
    }
}