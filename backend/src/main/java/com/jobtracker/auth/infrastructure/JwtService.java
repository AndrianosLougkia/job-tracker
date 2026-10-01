package com.jobtracker.auth.infrastructure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Handles JWT creation and validation.
 * Uses HS256 with a secret key supplied via environment variable.
 *
 * Claims stored in the token:
 *   sub  — userId (Long, as string)
 *   email — user email
 *   iat  — issued at
 *   exp  — expiry
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {

        if (secret.length() < 32) {
            throw new IllegalArgumentException(
                "JWT secret must be at least 32 characters (256 bits). " +
                "Set app.jwt.secret in your environment.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** Issues a signed JWT for the given user. */
    public String generateToken(Long userId, String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("email", email)
            .issuedAt(new Date(now))
            .expiration(new Date(now + expirationMs))
            .signWith(signingKey)
            .compact();
    }

    /** Extracts the userId from a valid token. */
    public Long extractUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    /** Extracts the email from a valid token. */
    public String extractEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    /**
     * Returns true if the token is structurally valid, correctly signed,
     * not expired, and the subject matches the given UserDetails username.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String email = extractEmail(token);
            return email.equals(userDetails.getUsername()) && !isExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
