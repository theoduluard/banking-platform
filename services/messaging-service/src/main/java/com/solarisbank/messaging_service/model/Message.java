package com.solarisbank.messaging_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "messages")
public class Message {

    public enum Type { INFO, WARNING, DOCUMENT, APPROVAL, REJECTION }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Recipient — the client's userId */
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false)
    private boolean isRead;

    /** Optional attachment (base64-encoded) */
    @Column(columnDefinition = "TEXT")
    private String attachmentBase64;

    @Column(length = 30)
    private String attachmentContentType;

    @Column(length = 200)
    private String attachmentFilename;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.isRead = false;
    }
}
