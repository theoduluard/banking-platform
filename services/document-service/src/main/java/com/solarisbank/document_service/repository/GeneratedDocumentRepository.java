package com.solarisbank.document_service.repository;

import com.solarisbank.document_service.model.GeneratedDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {
    List<GeneratedDocument> findByUserIdOrderByGeneratedAtDesc(UUID userId);
}
