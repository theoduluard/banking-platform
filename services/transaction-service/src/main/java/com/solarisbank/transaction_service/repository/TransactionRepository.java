package com.solarisbank.transaction_service.repository;

import com.solarisbank.transaction_service.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
            UUID fromAccountId, UUID toAccountId, Pageable pageable);

    // Used for idempotency: find an existing transaction by its client-generated key
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}
