package com.resumebuddy.service;

import com.resumebuddy.model.CreditTransaction;
import com.resumebuddy.model.User;
import com.resumebuddy.model.UserCredit;
import com.resumebuddy.model.dto.CreditBalanceDto;
import com.resumebuddy.model.dto.UserProfileDto;
import com.resumebuddy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserCreditService userCreditService;

    /**
     * Get full user profile with credit information
     */
    @Transactional(readOnly = true)
    public UserProfileDto getUserProfile(String userId) {
        log.info("Fetching profile for user {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (user.isDeleted()) {
            throw new RuntimeException("User account has been deleted");
        }

        UserCredit userCredit = userCreditService.getUserCredit(userId);

        UserProfileDto profile = new UserProfileDto();
        profile.setUserId(user.getId());
        profile.setEmail(user.getEmail());
        profile.setFullName(user.getFullName());
        profile.setAuthProvider(user.getAuthProvider().toString());
        profile.setCreatedAt(user.getCreatedAt());

        // Add credit info
        CreditBalanceDto creditBalance = new CreditBalanceDto();
        creditBalance.setUserId(userId);
        creditBalance.setTotalCredits(userCredit.getTotalCredits());
        creditBalance.setUsedCredits(userCredit.getUsedCredits());
        creditBalance.setAvailableCredits(userCredit.getAvailableCredits());
        profile.setCredits(creditBalance);

        return profile;
    }

    /**
     * Update user's full name
     */
    @Transactional
    public void updateUserName(String userId, String newName) {
        log.info("Updating name for user {} to '{}'", userId, newName);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (user.isDeleted()) {
            throw new RuntimeException("Cannot update deleted account");
        }

        user.setFullName(newName);
        userRepository.save(user);

        log.info("Successfully updated name for user {}", userId);
    }

    /**
     * Soft delete user account
     */
    @Transactional
    public void softDeleteAccount(String userId) {
        log.info("Soft deleting account for user {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (user.isDeleted()) {
            log.warn("User {} is already deleted", userId);
            return;
        }

        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Successfully soft deleted account for user {}", userId);
    }

    /**
     * Get transaction history for user (delegates to UserCreditService)
     */
    @Transactional(readOnly = true)
    public List<CreditTransaction> getTransactionHistory(String userId) {
        // Verify user exists and not deleted
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (user.isDeleted()) {
            throw new RuntimeException("User account has been deleted");
        }

        return userCreditService.getTransactionHistory(userId);
    }
}
