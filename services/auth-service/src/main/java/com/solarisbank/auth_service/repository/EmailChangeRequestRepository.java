package com.solarisbank.auth_service.repository;

import com.solarisbank.auth_service.model.EmailChangeRequest;
import com.solarisbank.auth_service.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmailChangeRequestRepository extends JpaRepository<EmailChangeRequest, UUID> {

    /** Delete all pending requests for a user (called before creating a new one). */
    void deleteByUser(User user);

    /**
     * Find a step-1 pending request: OTP sent but not yet verified.
     * Used in {@code confirmEmailChangeOtp()} to validate the submitted code.
     */
    @Query("""
        SELECT r FROM EmailChangeRequest r
        WHERE r.user = :user
          AND r.otpVerifiedAt IS NULL
          AND r.completedAt   IS NULL
        """)
    Optional<EmailChangeRequest> findPendingOtpByUser(@Param("user") User user);

    /**
     * Find a request by its verification token (step 3 — link click).
     * Matches any non-completed request regardless of OTP state.
     */
    Optional<EmailChangeRequest> findByVerifyToken(String verifyToken);

    /** Nightly cleanup: purge expired, non-completed requests. */
    int deleteByExpiresAtBeforeAndCompletedAtIsNull(LocalDateTime cutoff);
}
