package com.resumebuddy.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_credits")
@Data
public class UserCredit {

    @Id
    @Column(name = "user_id", length = 255)
    private String userId;

    @Column(name = "total_credits", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCredits = BigDecimal.ZERO;

    @Column(name = "used_credits", nullable = false, precision = 10, scale = 2)
    private BigDecimal usedCredits = BigDecimal.ZERO;

    @Column(name = "available_credits", precision = 10, scale = 2, insertable = false, updatable = false)
    private BigDecimal availableCredits;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean hasEnoughCredits(BigDecimal amount) {
        BigDecimal available = totalCredits.subtract(usedCredits);
        return available.compareTo(amount) >= 0;
    }
}
