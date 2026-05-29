package com.solarisbank.account_service.repository;

import com.solarisbank.account_service.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
