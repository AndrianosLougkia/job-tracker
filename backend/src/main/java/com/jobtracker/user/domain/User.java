package com.jobtracker.user.domain;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Persistent user account.
 * Implements UserDetails so Spring Security can use it directly in the filter chain.
 * Password is always stored as a BCrypt hash — never plaintext.
 */
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ------------------------------------------------------------------ //
    //  UserDetails implementation                                         //
    // ------------------------------------------------------------------ //

    /** Spring Security uses email as the username. */
    @Override
    public String getUsername() { return email; }

    /** Returns the BCrypt hash — Spring Security compares via PasswordEncoder. */
    @Override
    public String getPassword() { return passwordHash; }

    /** No role-based access control in MVP — single role for all users. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }

    @Override public boolean isAccountNonExpired()  { return true; }
    @Override public boolean isAccountNonLocked()   { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()            { return true; }

    // ------------------------------------------------------------------ //
    //  Domain getters                                                     //
    // ------------------------------------------------------------------ //

    public Long getId()             { return id; }
    public String getEmail()        { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt()   { return createdAt; }
    public Instant getUpdatedAt()   { return updatedAt; }

    public void setEmail(String email)                   { this.email = email; }
    public void setPasswordHash(String passwordHash)     { this.passwordHash = passwordHash; }
}
