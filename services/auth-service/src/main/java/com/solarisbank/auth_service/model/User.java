package com.solarisbank.auth_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    public enum Role {
        CLIENT, ADMIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String lastname;

    @Column(nullable = false)
    private String firstname;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private LocalDate createdAt;

    @Column(nullable = false)
    private Boolean isActive;

    @PrePersist
    public void prePersist(){
        this.createdAt = LocalDate.now();
        this.isActive = true;
    }
}
