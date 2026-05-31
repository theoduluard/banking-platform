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
@Table(name = "support_request_replies")
public class SupportRequestReply {

    public enum AuthorType { CLIENT, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuthorType authorType;

    /** userId of the author (client or admin) */
    @Column(nullable = false)
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /** Optional attachment */
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
    }
}
