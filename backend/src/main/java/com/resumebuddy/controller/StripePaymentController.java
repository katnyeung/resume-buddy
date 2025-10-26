package com.resumebuddy.controller;

import com.resumebuddy.model.PaymentRecord;
import com.resumebuddy.model.dto.CreateCheckoutSessionRequest;
import com.resumebuddy.model.dto.CreateCheckoutSessionResponse;
import com.resumebuddy.service.StripePaymentService;
import com.resumebuddy.security.JwtTokenProvider;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@Slf4j
@RequiredArgsConstructor
public class StripePaymentController {

    private final StripePaymentService stripePaymentService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Create Stripe Checkout Session
     */
    @PostMapping("/create-checkout-session")
    public ResponseEntity<?> createCheckoutSession(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CreateCheckoutSessionRequest request) {

        try {
            String token = authHeader.replace("Bearer ", "");
            String userId = jwtTokenProvider.getUserIdFromToken(token);

            log.info("Creating checkout session for user {} with package {}", userId, request.getPackageId());

            Session session = stripePaymentService.createCheckoutSession(userId, request.getPackageId());

            CreateCheckoutSessionResponse response = new CreateCheckoutSessionResponse(
                session.getId(),
                session.getUrl()
            );

            return ResponseEntity.ok(response);

        } catch (StripeException e) {
            log.error("Stripe API error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Payment processing error: " + e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to create checkout session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create checkout session"));
        }
    }

    /**
     * Stripe Webhook Endpoint (NO AUTH - validated by signature)
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            log.info("Received Stripe webhook");
            stripePaymentService.handleWebhook(payload, sigHeader);
            return ResponseEntity.ok("Webhook processed");

        } catch (SignatureVerificationException e) {
            log.error("Invalid webhook signature", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");

        } catch (Exception e) {
            log.error("Webhook processing error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook error");
        }
    }

    /**
     * Verify payment success (called from frontend after redirect)
     */
    @GetMapping("/success")
    public ResponseEntity<?> verifyPaymentSuccess(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("session_id") String sessionId) {

        try {
            String token = authHeader.replace("Bearer ", "");
            String userId = jwtTokenProvider.getUserIdFromToken(token);

            log.info("Verifying payment success for session {} and user {}", sessionId, userId);

            PaymentRecord payment = stripePaymentService.getPaymentBySessionId(sessionId);

            // Verify user owns this payment
            if (!payment.getUserId().equals(userId)) {
                log.warn("User {} attempted to access payment for user {}", userId, payment.getUserId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Unauthorized"));
            }

            // Build response map (Map.of doesn't allow null values, so use HashMap)
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("status", payment.getStatus().toString());
            response.put("creditsPurchased", payment.getCreditsPurchased());
            response.put("amountGbp", payment.getAmountGbp());
            response.put("completedAt", payment.getCompletedAt() != null ? payment.getCompletedAt().toString() : null);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to verify payment success", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to verify payment"));
        }
    }
}
