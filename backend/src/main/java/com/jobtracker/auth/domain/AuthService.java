package com.jobtracker.auth.domain;

import com.jobtracker.auth.api.AuthDtos;
import com.jobtracker.auth.infrastructure.JwtService;
import com.jobtracker.infrastructure.exception.DuplicateResourceException;
import com.jobtracker.infrastructure.exception.ResourceNotFoundException;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration and login.
 * BCrypt is used for password hashing — plaintext is never stored or logged.
 */
@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthDtos.RegisterResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                "An account with this email already exists");
        }

        String hash = passwordEncoder.encode(request.getPassword());
        User user = userRepository.save(new User(request.getEmail(), hash));

        return new AuthDtos.RegisterResponse(user.getId(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new InvalidCredentialsException());

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthDtos.LoginResponse(token, user.getId(), user.getEmail());
    }
}
