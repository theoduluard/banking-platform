package com.solarisbank.account_service.repository;

import com.solarisbank.account_service.model.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    List<Beneficiary> findByUserIdOrderByNameAsc(UUID userId);

    boolean existsByUserIdAndIban(UUID userId, String iban);
}
