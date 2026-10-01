package com.jobtracker.user.domain;

import com.jobtracker.infrastructure.exception.ResourceNotFoundException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User account operations.
 * Implements UserDetailsService so Spring Security can load users by email
 * during the authentication process.
 */
@Service
@Transactional
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Called by Spring Security during authentication.
     * Username in this application is the user's email address.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> ResourceNotFoundException.user(id));
    }

    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
