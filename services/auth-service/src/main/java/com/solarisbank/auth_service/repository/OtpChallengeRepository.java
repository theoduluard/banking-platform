package com.solarisbank.auth_service.repository;

import com.solarisbank.auth_service.model.OtpChallenge;
import com.solarisbank.auth_service.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    Optional<OtpChallenge> findBySessionToken(String sessionToken);

    /** Removes any pending challenge for this user before issuing a new one. */
    void deleteByUser(User user);

    /** Periodic cleanup: remove challenges that have already expired. Returns deleted count. */
    int deleteByExpiresAtBefore(LocalDateTime cutoff);
}
