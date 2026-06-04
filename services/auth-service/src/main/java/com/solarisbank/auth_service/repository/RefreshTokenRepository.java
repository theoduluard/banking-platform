package com.solarisbank.auth_service.repository;

import com.solarisbank.auth_service.model.RefreshToken;
import com.solarisbank.auth_service.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Look up a stored token by its SHA-256 hash. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Delete a single token by its hash (used during logout and rotation). */
    void deleteByTokenHash(String tokenHash);

    /** Revoke all sessions for a given user (e.g. password change, account suspension). */
    void deleteByUser(User user);

    /** Periodic cleanup: remove tokens that have already expired. */
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
