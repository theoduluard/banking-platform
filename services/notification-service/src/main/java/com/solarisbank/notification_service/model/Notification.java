package com.solarisbank.notification_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_id", columnList = "user_id"),
        @Index(name = "idx_notifications_user_read", columnList = "user_id, read")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    public enum Type {
        TRANSACTION_SENT,
        TRANSACTION_RECEIVED,
        TRANSACTION_FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The user this notification belongs to — injected by the Kafka consumer. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 512)
    private String message;

    /**
     * Whether the user has acknowledged this notification.
     * Default false — set to true by the PATCH /read endpoints.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
