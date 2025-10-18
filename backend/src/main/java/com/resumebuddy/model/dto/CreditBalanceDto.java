package com.resumebuddy.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreditBalanceDto {
    private String userId;
    private BigDecimal totalCredits;
    private BigDecimal usedCredits;
    private BigDecimal availableCredits;
}
