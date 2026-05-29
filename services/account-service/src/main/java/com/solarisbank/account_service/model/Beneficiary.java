package com.solarisbank.account_service.model;

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
@Table(
    name = "beneficiaries",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "iban"})
)
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** User who owns this beneficiary entry. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Human-readable label: "Papa", "Marie", "Loyer Paris"… */
    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 34)
    private String iban;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
