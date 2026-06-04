package com.solarisbank.auth_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Server-side refresh token store.
 * Only the SHA-256 hash of the raw token is persisted — never the plaintext.
 * This means a DB breach alone is not sufficient to impersonate a user.
 * Tokens are deleted on logout (explicit revocation) and rotated on every
 * refresh (single-use, prevents replay attacks).
 */
@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** SHA-256 hex-digest of the raw opaque token — unique per row. */
    @Column(name = "token_hash", unique = true, nullable = false)
    private String tokenHash;

    /** Owner of this token. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Absolute expiry timestamp — matched against DB time on every refresh call. */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
