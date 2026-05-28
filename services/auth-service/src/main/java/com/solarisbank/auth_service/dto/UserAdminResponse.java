package com.solarisbank.auth_service.dto;

import com.solarisbank.auth_service.model.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class UserAdminResponse {
    private UUID     userId;
    private String   email;
    private String   firstname;
    private String   lastname;
    private User.Role role;
    private Boolean  isActive;
    private LocalDate createdAt;
}
