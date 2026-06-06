package com.solarisbank.auth_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks an in-flight email-change request through its three-step lifecycle:
 *
 * <ol>
 *   <li>Created — OTP sent to the user's current email ({@code otpVerifiedAt} is null).</li>
 *   <li>OTP verified — verification link sent to the new email
 *       ({@code otpVerifiedAt} is set, {@code verifyToken} is populated).</li>
 *   <li>Completed — user clicked the link, email updated
 *       ({@code completedAt} is set).</li>
 * </ol>
 *
 * <p>At most one active (non-completed) request exists per user at any time;
 * old requests are deleted when a new one is created.
 */
@Entity
@Table(name = "email_change_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The requested new email address. */
    @Column(nullable = false)
    private String newEmail;

    /** SHA-256 hash of the 6-digit OTP sent to the current email. */
    private String otpCodeHash;

    /** Set when the user successfully submits the correct OTP (step 2). */
    private LocalDateTime otpVerifiedAt;

    /** Opaque UUID token embedded in the verification link sent to the new email. */
    @Column(unique = true)
    private String verifyToken;

    /** Set when the user clicks the verification link and the email is updated (step 3). */
    private LocalDateTime completedAt;

    /** Request expires (minutes for OTP phase, 1 h for link phase). */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
