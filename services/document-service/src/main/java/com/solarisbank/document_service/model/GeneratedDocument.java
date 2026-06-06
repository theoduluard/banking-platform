package com.solarisbank.document_service.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "generated_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GeneratedDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(nullable = false)
    private String filename;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;
}
