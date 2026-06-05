package com.solarisbank.auth_service.controller;

import com.solarisbank.auth_service.dto.UserAdminResponse;
import com.solarisbank.auth_service.exception.BusinessException;
import com.solarisbank.auth_service.model.User;
import com.solarisbank.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin endpoints — access is enforced at the API Gateway layer (X-User-Role=ADMIN).
 * We validate the header here as defence-in-depth.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final int MAX_PAGE_SIZE = 200;

    private final UserRepository userRepository;

    // ── GET /api/v1/admin/users ────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<Page<UserAdminResponse>> getAllUsers(
            @RequestHeader("X-User-Role")               String userRole,
            @RequestParam(defaultValue = "0")    int    page,
            @RequestParam(defaultValue = "50")   int    size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc")  String sortDir,
            @RequestParam(required = false)      String search,
            @RequestParam(required = false)      String role,
            @RequestParam(required = false)      String status) {

        requireAdmin(userRole);

        // Map frontend sort key → entity field name
        String sortField = switch (sortBy) {
            case "email" -> "email";
            case "date"  -> "createdAt";
            default      -> "lastname";   // "name" or unknown → lastname
        };

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortField).descending()
                : Sort.by(sortField).ascending();

        PageRequest pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), sort);

        User.Role roleEnum = (role != null && !"ALL".equals(role))
                ? User.Role.valueOf(role) : null;

        Boolean isActive = null;
        if ("ACTIVE".equals(status))   isActive = true;
        if ("INACTIVE".equals(status)) isActive = false;

        // Treat blank search as no filter
        String searchParam = (search != null && !search.isBlank()) ? search : null;

        Page<UserAdminResponse> result = userRepository
                .findWithFilters(searchParam, roleEnum, isActive, pageable)
                .map(this::toResponse);

        return ResponseEntity.ok(result);
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
