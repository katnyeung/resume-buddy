package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CertificationDto {
    private String id;
    private String certificationName;
    private String issuingOrganization;
    private String issueDate;
    private String credentialId;
}
