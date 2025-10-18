package com.resumebuddy.service;

import com.resumebuddy.model.CreditTransaction;
import com.resumebuddy.model.CreditTransaction.TransactionType;
import com.resumebuddy.model.UserCredit;
import com.resumebuddy.repository.CreditTransactionRepository;
import com.resumebuddy.repository.UserCreditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserCreditService {

    private final UserCreditRepository userCreditRepository;
    private final CreditTransactionRepository creditTransactionRepository;

    /**
     * Check if user has enough credits
     */
    @Transactional(readOnly = true)
    public boolean hasEnoughCredits(String userId, BigDecimal amount) {
        UserCredit userCredit = getUserCredit(userId);
        boolean hasEnough = userCredit.hasEnoughCredits(amount);
        log.debug("User {} has {} available credits, needs {}: {}",
            userId, userCredit.getAvailableCredits(), amount, hasEnough);
        return hasEnough;
    }

    /**
     * Deduct credits from user account (with pessimistic locking)
     * @throws InsufficientCreditsException if not enough credits
     */
    @Transactional
    public void deductCredits(String userId, BigDecimal amount, String jobId, String description) {
        log.info("Deducting {} credits from user {} for job {}", amount, userId, jobId);

        // Lock row to prevent concurrent modification
        UserCredit userCredit = userCreditRepository.findByIdWithLock(userId)
            .orElseGet(() -> createDefaultUserCredit(userId));

        BigDecimal balanceBefore = userCredit.getTotalCredits().subtract(userCredit.getUsedCredits());

        if (!userCredit.hasEnoughCredits(amount)) {
            throw new InsufficientCreditsException(
                String.format("Insufficient credits. Available: %s, Required: %s",
                    balanceBefore, amount));
        }

        // Deduct credits
        userCredit.setUsedCredits(userCredit.getUsedCredits().add(amount));
        userCreditRepository.save(userCredit);

        BigDecimal balanceAfter = userCredit.getTotalCredits().subtract(userCredit.getUsedCredits());

        // Record transaction
        CreditTransaction transaction = new CreditTransaction();
        transaction.setUserId(userId);
        transaction.setAmount(amount.negate()); // Negative for deduction
        transaction.setTransactionType(TransactionType.USAGE);
        transaction.setJobId(jobId);
        transaction.setDescription(description);
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(balanceAfter);
        creditTransactionRepository.save(transaction);

        log.info("Successfully deducted {} credits from user {}. Balance: {} → {}",
            amount, userId, balanceBefore, balanceAfter);
    }

    /**
     * Refund credits to user account
     */
    @Transactional
    public void refundCredits(String userId, BigDecimal amount, String jobId, String reason) {
        log.info("Refunding {} credits to user {} for job {}. Reason: {}", amount, userId, jobId, reason);

        UserCredit userCredit = userCreditRepository.findByIdWithLock(userId)
            .orElseThrow(() -> new RuntimeException("User credit account not found: " + userId));

        BigDecimal balanceBefore = userCredit.getTotalCredits().subtract(userCredit.getUsedCredits());

        // Refund credits (reduce used_credits)
        BigDecimal newUsedCredits = userCredit.getUsedCredits().subtract(amount);
        if (newUsedCredits.compareTo(BigDecimal.ZERO) < 0) {
            newUsedCredits = BigDecimal.ZERO;
        }
        userCredit.setUsedCredits(newUsedCredits);
        userCreditRepository.save(userCredit);

        BigDecimal balanceAfter = userCredit.getTotalCredits().subtract(userCredit.getUsedCredits());

        // Record transaction
        CreditTransaction transaction = new CreditTransaction();
        transaction.setUserId(userId);
        transaction.setAmount(amount); // Positive for refund
        transaction.setTransactionType(TransactionType.REFUND);
        transaction.setJobId(jobId);
        transaction.setDescription(reason);
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(balanceAfter);
        creditTransactionRepository.save(transaction);

        log.info("Successfully refunded {} credits to user {}. Balance: {} → {}",
            amount, userId, balanceBefore, balanceAfter);
    }

    /**
     * Grant credits to user (admin function)
     */
    @Transactional
    public void grantCredits(String userId, BigDecimal amount, String reason) {
        log.info("Granting {} credits to user {}. Reason: {}", amount, userId, reason);

        UserCredit userCredit = userCreditRepository.findByIdWithLock(userId)
            .orElseGet(() -> createDefaultUserCredit(userId));

        BigDecimal balanceBefore = userCredit.getTotalCredits().subtract(userCredit.getUsedCredits());

        // Add to total credits
        userCredit.setTotalCredits(userCredit.getTotalCredits().add(amount));
        userCreditRepository.save(userCredit);

        BigDecimal balanceAfter = userCredit.getTotalCredits().subtract(userCredit.getUsedCredits());

        // Record transaction
        CreditTransaction transaction = new CreditTransaction();
        transaction.setUserId(userId);
        transaction.setAmount(amount);
        transaction.setTransactionType(TransactionType.ADMIN_ADJUSTMENT);
        transaction.setDescription(reason);
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(balanceAfter);
        creditTransactionRepository.save(transaction);

        log.info("Successfully granted {} credits to user {}. Balance: {} → {}",
            amount, userId, balanceBefore, balanceAfter);
    }

    /**
     * Get user credit balance (creates default if not exists)
     */
    @Transactional
    public UserCredit getUserCredit(String userId) {
        return userCreditRepository.findById(userId)
            .orElseGet(() -> createDefaultUserCredit(userId));
    }

    /**
     * Get transaction history for user
     */
    @Transactional(readOnly = true)
    public List<CreditTransaction> getTransactionHistory(String userId) {
        return creditTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Create default user credit account with initial credits
     */
    private UserCredit createDefaultUserCredit(String userId) {
        log.info("Creating default credit account for user {}", userId);
        UserCredit userCredit = new UserCredit();
        userCredit.setUserId(userId);
        userCredit.setTotalCredits(new BigDecimal("1000.00")); // Default 1000 free credits
        userCredit.setUsedCredits(BigDecimal.ZERO);
        return userCreditRepository.save(userCredit);
    }

    /**
     * Custom exception for insufficient credits
     */
    public static class InsufficientCreditsException extends RuntimeException {
        public InsufficientCreditsException(String message) {
            super(message);
        }
    }
}
