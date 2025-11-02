package com.resumebuddy.model.dto;

import lombok.Data;

@Data
public class NotificationRequest {
    private String userId;
    private String subject;
    private String body;
}
