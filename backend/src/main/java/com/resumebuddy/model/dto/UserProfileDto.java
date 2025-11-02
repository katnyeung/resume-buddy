package com.resumebuddy.model.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserProfileDto {
    private String userId;
    private String email;
    private String fullName;
    private String authProvider;
    private LocalDateTime createdAt;

    // Credit info
    private CreditBalanceDto credits;
}
