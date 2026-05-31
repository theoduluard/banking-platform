package com.solarisbank.account_service.repository;

import com.solarisbank.account_service.model.VerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, UUID> {
    Optional<VerificationDocument> findByAccountId(UUID accountId);
    boolean existsByUserId(UUID userId);
}
