package com.resumebuddy.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_transactions")
@Data
public class CreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(name = "job_id", length = 255)
    private String jobId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "balance_before", precision = 10, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum TransactionType {
        PURCHASE,
        USAGE,
        REFUND,
        ADMIN_ADJUSTMENT
    }
}
