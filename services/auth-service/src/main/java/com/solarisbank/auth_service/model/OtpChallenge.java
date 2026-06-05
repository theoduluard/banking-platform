package com.solarisbank.auth_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents an in-flight 2FA OTP challenge.
 * Created on login; consumed (deleted) on successful verification.
 * Expired entries are purged nightly by the scheduled cleanup job.
 */
@Entity
@Table(name = "otp_challenges")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Opaque token given to the frontend to identify this challenge. */
    @Column(nullable = false, unique = true)
    private String sessionToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 hash of the 6-digit code — never stored in plain text. */
    @Column(nullable = false)
    private String codeHash;

    /** Number of failed verification attempts; challenge is deleted after 3. */
    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
