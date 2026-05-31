package com.solarisbank.messaging_service.repository;

import com.solarisbank.messaging_service.model.SupportRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupportRequestRepository extends JpaRepository<SupportRequest, UUID> {
    List<SupportRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Page<SupportRequest> findByStatusOrderByCreatedAtDesc(SupportRequest.Status status, Pageable pageable);
    Page<SupportRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByStatus(SupportRequest.Status status);
}
