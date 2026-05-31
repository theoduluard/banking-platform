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
@Table(name = "verification_documents")
public class VerificationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Nullable — KYC is now submitted at registration time, before any account exists.
    // Set to null when submitted via POST /api/v1/accounts/kyc.
    @Column(nullable = true)
    private UUID accountId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String selfieBase64;

    @Column(nullable = false, length = 30)
    private String selfieContentType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String idCardBase64;

    @Column(nullable = false, length = 30)
    private String idCardContentType;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @PrePersist
    public void prePersist() {
        this.submittedAt = LocalDateTime.now();
    }
}
