package com.resumebuddy.controller;

import com.resumebuddy.model.CreditTransaction;
import com.resumebuddy.model.dto.UpdateUserProfileRequest;
import com.resumebuddy.model.dto.UserProfileDto;
import com.resumebuddy.service.UserProfileService;
import com.resumebuddy.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@Slf4j
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Get user profile
     */
    @GetMapping("/{userId}/profile")
    public ResponseEntity<?> getUserProfile(
            @PathVariable String userId,
            @RequestHeader("Authorization") String authHeader) {

        try {
            String token = authHeader.replace("Bearer ", "");
            String requestingUserId = jwtTokenProvider.getUserIdFromToken(token);

            // Verify user is accessing their own profile
            if (!requestingUserId.equals(userId)) {
                log.warn("User {} attempted to access profile of user {}", requestingUserId, userId);
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
            }

            UserProfileDto profile = userProfileService.getUserProfile(userId);
            return ResponseEntity.ok(profile);

        } catch (Exception e) {
            log.error("Failed to get profile for user {}", userId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update user's full name
     */
    @PutMapping("/{userId}/profile/name")
    public ResponseEntity<?> updateUserName(
            @PathVariable String userId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody UpdateUserProfileRequest request) {

        try {
            String token = authHeader.replace("Bearer ", "");
            String requestingUserId = jwtTokenProvider.getUserIdFromToken(token);

            if (!requestingUserId.equals(userId)) {
                log.warn("User {} attempted to update profile of user {}", requestingUserId, userId);
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
            }

            userProfileService.updateUserName(userId, request.getFullName());
            return ResponseEntity.ok(Map.of("message", "Name updated successfully"));

        } catch (Exception e) {
            log.error("Failed to update name for user {}", userId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Soft delete user account
     */
    @DeleteMapping("/{userId}/account")
    public ResponseEntity<?> deleteAccount(
            @PathVariable String userId,
            @RequestHeader("Authorization") String authHeader) {

        try {
            String token = authHeader.replace("Bearer ", "");
            String requestingUserId = jwtTokenProvider.getUserIdFromToken(token);

            if (!requestingUserId.equals(userId)) {
                log.warn("User {} attempted to delete account of user {}", requestingUserId, userId);
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
            }

            userProfileService.softDeleteAccount(userId);
            return ResponseEntity.ok(Map.of("message", "Account deleted successfully"));

        } catch (Exception e) {
            log.error("Failed to delete account for user {}", userId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get transaction history
     */
    @GetMapping("/{userId}/profile/transactions")
    public ResponseEntity<?> getTransactionHistory(
            @PathVariable String userId,
            @RequestHeader("Authorization") String authHeader) {

        try {
            String token = authHeader.replace("Bearer ", "");
            String requestingUserId = jwtTokenProvider.getUserIdFromToken(token);

            if (!requestingUserId.equals(userId)) {
                log.warn("User {} attempted to access transactions of user {}", requestingUserId, userId);
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
            }

            List<CreditTransaction> transactions = userProfileService.getTransactionHistory(userId);
            return ResponseEntity.ok(transactions);

        } catch (Exception e) {
            log.error("Failed to get transaction history for user {}", userId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }
}
