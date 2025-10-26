package com.resumebuddy.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateCheckoutSessionResponse {
    private String sessionId;
    private String checkoutUrl;
}
