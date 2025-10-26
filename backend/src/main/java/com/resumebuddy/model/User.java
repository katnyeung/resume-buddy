package com.resumebuddy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash; // Nullable for Google OAuth users

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "google_id", unique = true, length = 255)
    private String googleId; // Nullable, unique identifier from Google

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.EMAIL;

    @Column(name = "role", nullable = false, length = 20)
    private String role = "USER"; // Default role: USER, can be ADMIN

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public enum AuthProvider {
        EMAIL,
        GOOGLE
    }

    /**
     * Check if user account is deleted
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
