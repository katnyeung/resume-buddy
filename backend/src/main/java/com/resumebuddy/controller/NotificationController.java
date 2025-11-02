package com.resumebuddy.controller;

import com.resumebuddy.model.User;
import com.resumebuddy.model.dto.NotificationRequest;
import com.resumebuddy.repository.UserRepository;
import com.resumebuddy.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for sending notifications (email) to users.
 * Used by interview-practice-service for round reminders.
 */
@RestController
@RequestMapping("/api/notifications")
@Slf4j
@RequiredArgsConstructor
public class NotificationController {

    private final EmailService emailService;
    private final UserRepository userRepository;

    /**
     * Send email notification to user.
     * Used by interview-practice-service for scheduled round reminders.
     *
     * @param request NotificationRequest with userId, subject, body
     * @return Success/error response
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendNotification(@RequestBody NotificationRequest request) {
        try {
            // Validate request
            if (request.getUserId() == null || request.getSubject() == null || request.getBody() == null) {
                log.warn("Invalid notification request: missing required fields");
                return ResponseEntity.badRequest()
                    .body(Map.of(
                        "sent", false,
                        "error", "Missing required fields: userId, subject, body"
                    ));
            }

            // Fetch user from database
            User user = userRepository.findById(request.getUserId())
                .orElse(null);

            if (user == null) {
                log.warn("User not found: {}", request.getUserId());
                return ResponseEntity.badRequest()
                    .body(Map.of(
                        "sent", false,
                        "error", "User not found: " + request.getUserId()
                    ));
            }

            // Check if user account is deleted
            if (user.isDeleted()) {
                log.warn("Attempted to send email to deleted user: {}", request.getUserId());
                return ResponseEntity.badRequest()
                    .body(Map.of(
                        "sent", false,
                        "error", "User account is deleted"
                    ));
            }

            // Send email
            emailService.sendEmail(user.getEmail(), request.getSubject(), request.getBody());

            log.info("Notification sent to user {} ({}): {}", request.getUserId(), user.getEmail(), request.getSubject());

            return ResponseEntity.ok(Map.of(
                "sent", true,
                "message", "Email sent successfully"
            ));

        } catch (MailException e) {
            log.error("Failed to send email notification: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of(
                    "sent", false,
                    "error", "Email service error: " + e.getMessage()
                ));

        } catch (Exception e) {
            log.error("Unexpected error sending notification: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of(
                    "sent", false,
                    "error", "Internal server error"
                ));
        }
    }
}
