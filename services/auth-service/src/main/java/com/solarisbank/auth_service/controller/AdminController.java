package com.solarisbank.auth_service.controller;

import com.solarisbank.auth_service.dto.UserAdminResponse;
import com.solarisbank.auth_service.exception.BusinessException;
import com.solarisbank.auth_service.model.User;
import com.solarisbank.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin endpoints — access is enforced at the API Gateway layer (X-User-Role=ADMIN).
 * We validate the header here as defence-in-depth.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;

    // ── GET /api/v1/admin/users ────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<List<UserAdminResponse>> getAllUsers(
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);

        List<UserAdminResponse> users = userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(users);
    }

    // ── PATCH /api/v1/admin/users/{id}/status ─────────────────────────────────

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<UserAdminResponse> updateUserStatus(
            @PathVariable UUID id,
            @RequestParam boolean active,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));

        // Prevent deactivating admins
        if (user.getRole() == User.Role.ADMIN && !active) {
            throw new BusinessException("Cannot deactivate an admin account", HttpStatus.FORBIDDEN);
        }

        user.setIsActive(active);
        userRepository.save(user);

        return ResponseEntity.ok(toResponse(user));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN);
        }
    }

    private UserAdminResponse toResponse(User u) {
        return UserAdminResponse.builder()
                .userId(u.getUserId())
                .email(u.getEmail())
                .firstname(u.getFirstname())
                .lastname(u.getLastname())
                .role(u.getRole())
                .isActive(u.getIsActive())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
