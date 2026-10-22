package com.billetterie.user_service.user;

import com.billetterie.user_service.dto.RegisterRequest;
import com.billetterie.user_service.dto.UpdateProfileRequest;
import com.billetterie.user_service.exception.EmailAlreadyUsedException;
import com.billetterie.user_service.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void register_hashesPasswordAndNormalizesInput() {
        when(userRepository.existsByEmail("yassine@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.register(
                new RegisterRequest("  Yassine@Gmail.com ", "password123", " Yassine ", " Test ", Role.USER));

        assertThat(saved.getEmail()).isEqualTo("yassine@gmail.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(saved.getFirstName()).isEqualTo("Yassine");
        assertThat(saved.getLastName()).isEqualTo("Test");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void register_throwsWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail("yassine@gmail.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(
                new RegisterRequest("yassine@gmail.com", "password123", "Yassine", "Test", Role.USER)))
                .isInstanceOf(EmailAlreadyUsedException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void register_translatesUniqueConstraintViolationIntoEmailAlreadyUsed() {
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> userService.register(
                new RegisterRequest("yassine@gmail.com", "password123", "Yassine", "Test", Role.USER)))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void updateProfile_changesNamesAndTrimsThem() {
        UUID id = UUID.randomUUID();
        User user = new User("yassine@gmail.com", "hash", "Old", "Name", Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        User updated = userService.updateProfile(id, new UpdateProfileRequest(" New ", " Name2 "));

        assertThat(updated.getFirstName()).isEqualTo("New");
        assertThat(updated.getLastName()).isEqualTo("Name2");
        assertThat(updated.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void getById_throwsWhenUserDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(id))
                .isInstanceOf(UserNotFoundException.class);
    }
}