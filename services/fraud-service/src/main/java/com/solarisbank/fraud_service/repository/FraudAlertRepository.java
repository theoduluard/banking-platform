package com.solarisbank.fraud_service.repository;

import com.solarisbank.fraud_service.model.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {
    List<FraudAlert> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<FraudAlert> findByStatusOrderByCreatedAtDesc(FraudAlert.AlertStatus status);
}
