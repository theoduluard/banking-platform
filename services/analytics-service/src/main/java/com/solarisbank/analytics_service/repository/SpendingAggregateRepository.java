package com.solarisbank.analytics_service.repository;

import com.solarisbank.analytics_service.model.SpendingAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpendingAggregateRepository extends JpaRepository<SpendingAggregate, UUID> {
    List<SpendingAggregate> findByUserIdAndYearAndMonthOrderByCategoryAsc(UUID userId, short year, short month);
    List<SpendingAggregate> findByUserIdOrderByYearDescMonthDesc(UUID userId);
    Optional<SpendingAggregate> findByUserIdAndAccountIdAndYearAndMonthAndCategory(UUID userId, UUID accountId, short year, short month, String category);
}
