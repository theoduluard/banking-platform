package com.solarisbank.account_service.repository;

import com.solarisbank.account_service.model.ProcessedSagaEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedSagaEventRepository extends JpaRepository<ProcessedSagaEvent, UUID> {

    boolean existsByTransactionIdAndEventType(UUID transactionId, String eventType);
}
