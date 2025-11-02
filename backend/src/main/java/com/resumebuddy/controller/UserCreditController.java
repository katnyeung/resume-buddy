package com.resumebuddy.controller;

import com.resumebuddy.model.CreditTransaction;
import com.resumebuddy.model.UserCredit;
import com.resumebuddy.model.dto.CreditBalanceDto;
import com.resumebuddy.service.UserCreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@Slf4j
@RequiredArgsConstructor
public class UserCreditController {

    private final UserCreditService userCreditService;

    /**
     * Get user credit balance
     */
    @GetMapping("/{userId}/credits")
    public ResponseEntity<CreditBalanceDto> getCreditBalance(@PathVariable String userId) {
        try {
            log.debug("Fetching credit balance for user: {}", userId);
            UserCredit userCredit = userCreditService.getUserCredit(userId);

            CreditBalanceDto balance = new CreditBalanceDto();
            balance.setUserId(userCredit.getUserId());
            balance.setTotalCredits(userCredit.getTotalCredits());
            balance.setUsedCredits(userCredit.getUsedCredits());
            balance.setAvailableCredits(userCredit.getAvailableCredits());

            log.debug("Successfully fetched credits for user {}: available={}", userId, balance.getAvailableCredits());
            return ResponseEntity.ok(balance);

        } catch (Exception e) {
            log.error("Failed to get credit balance for user: {} - Error: {}", userId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get user credit transaction history
     */
    @GetMapping("/{userId}/credits/transactions")
    public ResponseEntity<List<CreditTransaction>> getTransactionHistory(@PathVariable String userId) {
        try {
            List<CreditTransaction> transactions = userCreditService.getTransactionHistory(userId);
            return ResponseEntity.ok(transactions);

        } catch (Exception e) {
            log.error("Failed to get transaction history for user {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Deduct credits from user account
     * Used by interview-practice-service and other microservices
     */
    @PostMapping("/{userId}/credits/deduct")
    public ResponseEntity<Map<String, Object>> deductCredits(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request) {

        try {
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            String jobId = (String) request.getOrDefault("jobId", "UNKNOWN");
            String description = (String) request.getOrDefault("description", "Credit deduction");

            log.info("Deducting {} credits from user {} for job {}", amount, userId, jobId);
            userCreditService.deductCredits(userId, amount, jobId, description);

            // Get updated balance
            UserCredit userCredit = userCreditService.getUserCredit(userId);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Credits deducted successfully",
                "amountDeducted", amount,
                "remainingCredits", userCredit.getAvailableCredits()
            ));

        } catch (Exception e) {
            log.error("Failed to deduct credits for user {}", userId, e);
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
        }
    }

    /**
     * Admin endpoint: Grant credits to user
     */
    @PostMapping("/admin/credits/grant")
    public ResponseEntity<Map<String, String>> grantCredits(
            @RequestBody Map<String, Object> request) {

        try {
            String userId = (String) request.get("userId");
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            String reason = (String) request.getOrDefault("reason", "Admin credit grant");

            userCreditService.grantCredits(userId, amount, reason);

            return ResponseEntity.ok(Map.of(
                "message", String.format("Successfully granted %s credits to user %s", amount, userId)
            ));

        } catch (Exception e) {
            log.error("Failed to grant credits", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }
}
