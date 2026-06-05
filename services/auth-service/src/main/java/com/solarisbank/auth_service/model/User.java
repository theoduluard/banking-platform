package com.solarisbank.auth_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    public enum Role {
        CLIENT, ADMIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String lastname;

    @Column(nullable = false)
    private String firstname;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private LocalDate createdAt;

    @Column(nullable = false)
    private Boolean isActive;

    /**
     * Set to false on registration; becomes true once the user clicks the
     * verification link in the email.  NULL means the user was created before
     * email-verification was introduced (treated as verified for backward compat).
     */
    private Boolean emailVerified;

    /** UUID token sent by email — null once verified or not yet set. */
    @Column(unique = true)
    private String emailVerificationToken;

    /** Token expires 24 h after issuance. */
    private LocalDateTime emailVerificationTokenExpiry;

    /** UUID token sent for password reset — null once used or not yet set. */
    @Column(unique = true)
    private String passwordResetToken;

    /** Password reset token expires 1 h after issuance. */
    private LocalDateTime passwordResetTokenExpiry;

    /**
     * Consecutive failed login attempts since the last successful login.
     * Reset to 0 on success; incremented on BadCredentialsException.
     */
    @Column(nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    /**
     * When non-null the account is locked and login is refused until this instant.
     * Thresholds: ≥5 attempts → +30 s, ≥10 → +5 min, ≥15 → +1 h.
     * Reset to null on successful authentication.
     */
    private LocalDateTime lockedUntil;

    @PrePersist
    public void prePersist(){
        this.createdAt = LocalDate.now();
        if (this.isActive      == null) this.isActive      = true;
        if (this.emailVerified == null) this.emailVerified = false;
    }
}
