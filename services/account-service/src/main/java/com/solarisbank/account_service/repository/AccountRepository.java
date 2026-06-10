package com.solarisbank.account_service.repository;

import com.solarisbank.account_service.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByUserId(UUID userId);
    boolean existsByIban(String iban);
    Optional<Account> findByIban(String iban);
    Optional<Account> findByAccountIdAndUserId(UUID id, UUID userId);
    List<Account> findByStatus(Account.Status status);
    List<Account> findByUserIdAndType(UUID userId, Account.Type type);
    long countByUserId(UUID userId);

    /**
     * Acquires a pessimistic write lock (SELECT … FOR UPDATE) before returning the account.
     * Used by debit/credit operations to prevent concurrent overdraft (lost-update anomaly).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :accountId AND a.userId = :userId")
    Optional<Account> findWithLockByAccountIdAndUserId(
            @Param("accountId") UUID accountId,
            @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :id")
    Optional<Account> findWithLockById(@Param("id") UUID id);
}
