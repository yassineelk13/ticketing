package com.billetterie.user_service.user;

import com.billetterie.user_service.dto.RegisterRequest;
import com.billetterie.user_service.dto.UpdateProfileRequest;
import com.billetterie.user_service.exception.EmailAlreadyUsedException;
import com.billetterie.user_service.exception.UserNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName().trim(),
                request.lastName().trim(),
                request.role()
        );

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException(email);
        }
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional
    public User updateProfile(UUID id, UpdateProfileRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        user.updateProfile(request.firstName().trim(), request.lastName().trim());
        return user;
    }
}