package com.resumebuddy.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "resume_analysis_certification")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeAnalysisCertification {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ResumeAnalysis analysis;

    @Column(name = "certification_name", length = 255)
    private String certificationName;

    @Column(name = "issuing_organization", length = 255)
    private String issuingOrganization;

    @Column(name = "issue_date", length = 100)
    private String issueDate;

    @Column(name = "credential_id", length = 255)
    private String credentialId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
