package com.jobtracker.auth.domain;

import com.jobtracker.auth.api.AuthDtos;
import com.jobtracker.auth.infrastructure.JwtService;
import com.jobtracker.infrastructure.exception.BadRequestException;
import com.jobtracker.infrastructure.exception.DuplicateResourceException;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@Transactional
public class AuthService {

    /** BCrypt only reads the first 72 bytes and Spring's encoder rejects anything longer. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       LoginAttemptLimiter loginAttemptLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    public AuthDtos.RegisterResponse register(AuthDtos.RegisterRequest request) {
        String email = normaliseEmail(request.getEmail());
        if (passwordBytes(request.getPassword()) > MAX_PASSWORD_BYTES) {
            throw new BadRequestException("Password must not exceed " + MAX_PASSWORD_BYTES
                + " bytes; use fewer non-ASCII characters");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        User user = userRepository.save(new User(email, passwordEncoder.encode(request.getPassword())));
        return new AuthDtos.RegisterResponse(user.getId(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        String email = normaliseEmail(request.getEmail());
        loginAttemptLimiter.checkAllowed(email);
        User user = userRepository.findByEmail(email).orElse(null);
        // A password over the BCrypt limit can never match, and the encoder would throw on it
        if (user == null || passwordBytes(request.getPassword()) > MAX_PASSWORD_BYTES
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptLimiter.recordFailure(email);
            throw new InvalidCredentialsException();
        }
        loginAttemptLimiter.recordSuccess(email);
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthDtos.LoginResponse(token, user.getId(), user.getEmail());
    }

    /** Emails are case-insensitive and stored lower-case (enforced by the database). */
    static String normaliseEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static int passwordBytes(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length;
    }
}
