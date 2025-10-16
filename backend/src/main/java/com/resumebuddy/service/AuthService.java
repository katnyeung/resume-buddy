package com.resumebuddy.service;

import com.resumebuddy.model.User;
import com.resumebuddy.repository.UserRepository;
import com.resumebuddy.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * Register a new user with email and password
     */
    @Transactional
    public User registerUser(String email, String password, String fullName) {
        // Check if user already exists
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered");
        }

        // Create new user
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setAuthProvider(User.AuthProvider.EMAIL);

        User savedUser = userRepository.save(user);
        logger.info("New user registered: {}", email);

        return savedUser;
    }

    /**
     * Authenticate user and generate JWT token
     */
    public String login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        // Check if user registered via Google
        if (user.getAuthProvider() == User.AuthProvider.GOOGLE) {
            throw new RuntimeException("Please sign in with Google");
        }

        // Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        // Generate JWT token
        String token = jwtTokenProvider.generateToken(user.getId());
        logger.info("User logged in: {}", email);

        return token;
    }

    /**
     * Find or create user from Google OAuth
     */
    @Transactional
    public User findOrCreateGoogleUser(String googleId, String email, String fullName) {
        // Try to find by Google ID first
        Optional<User> existingUser = userRepository.findByGoogleId(googleId);
        if (existingUser.isPresent()) {
            logger.info("Existing Google user logged in: {}", email);
            return existingUser.get();
        }

        // Try to find by email (user might have registered with email first)
        existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            // Link Google account to existing user
            User user = existingUser.get();
            user.setGoogleId(googleId);
            user.setAuthProvider(User.AuthProvider.GOOGLE);
            User savedUser = userRepository.save(user);
            logger.info("Linked Google account to existing user: {}", email);
            return savedUser;
        }

        // Create new user from Google
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setFullName(fullName);
        newUser.setGoogleId(googleId);
        newUser.setAuthProvider(User.AuthProvider.GOOGLE);
        newUser.setPasswordHash(null); // No password for Google users

        User savedUser = userRepository.save(newUser);
        logger.info("New Google user registered: {}", email);

        return savedUser;
    }

    /**
     * Get user by ID
     */
    public User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
