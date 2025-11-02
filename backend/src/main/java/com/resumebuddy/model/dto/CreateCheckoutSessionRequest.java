package com.resumebuddy.model.dto;

import lombok.Data;

@Data
public class CreateCheckoutSessionRequest {
    private String packageId; // "200", "500", or "1000"
}
