package com.solarisbank.transaction_service.repository;

import com.solarisbank.transaction_service.model.ScheduledTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduledTransferRepository extends JpaRepository<ScheduledTransfer, UUID> {

    List<ScheduledTransfer> findByInitiatedByUserIdAndActiveTrue(UUID userId);

    /** Returns all active scheduled transfers whose next execution date is today or earlier. */
    List<ScheduledTransfer> findByActiveTrueAndNextExecutionDateLessThanEqual(LocalDate date);
}
