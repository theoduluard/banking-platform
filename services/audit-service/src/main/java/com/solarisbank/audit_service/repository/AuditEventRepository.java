package com.solarisbank.audit_service.repository;

import com.solarisbank.audit_service.model.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    Page<AuditEvent> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<AuditEvent> findByEventTypeOrderByCreatedAtDesc(String eventType, Pageable pageable);
    Page<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
