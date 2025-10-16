package com.resumebuddy.controller;

import com.resumebuddy.model.User;
import com.resumebuddy.model.dto.AuthResponse;
import com.resumebuddy.model.dto.LoginRequest;
import com.resumebuddy.model.dto.RegisterRequest;
import com.resumebuddy.model.dto.UserDto;
import com.resumebuddy.security.JwtTokenProvider;
import com.resumebuddy.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * Register a new user
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = authService.registerUser(
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName()
            );

            String token = jwtTokenProvider.generateToken(user.getId());

            AuthResponse response = new AuthResponse(
                    token,
                    user.getId(),
                    user.getEmail(),
                    user.getFullName()
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Registration failed", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * Login with email and password
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            String token = authService.login(request.getEmail(), request.getPassword());
            User user = authService.getUserById(jwtTokenProvider.getUserIdFromToken(token));

            AuthResponse response = new AuthResponse(
                    token,
                    user.getId(),
                    user.getEmail(),
                    user.getFullName()
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Login failed", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    /**
     * Get current authenticated user
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        try {
            String userId = (String) authentication.getPrincipal();
            User user = authService.getUserById(userId);

            UserDto userDto = new UserDto(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getAuthProvider().name()
            );

            return ResponseEntity.ok(userDto);
        } catch (Exception e) {
            logger.error("Failed to get current user", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }
}
