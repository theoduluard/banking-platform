package com.solarisbank.auth_service.controller;

import com.solarisbank.auth_service.model.User;
import com.solarisbank.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Internal service-to-service endpoints — not exposed through the API gateway.
 * All calls must supply the correct X-Internal-Secret header.
 */
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalController {

    private final UserRepository userRepository;

    @Value("${internal.secret:changeme-dev-only}")
    private String internalSecret;

    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getUserInfo(
            @PathVariable UUID userId,
            @RequestHeader("X-Internal-Secret") String secret) {

        if (!internalSecret.equals(secret)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        return userRepository.findById(userId)
                .map(u -> ResponseEntity.ok(Map.of(
                        "userId",    u.getUserId().toString(),
                        "firstName", u.getFirstname(),
                        "lastName",  u.getLastname(),
                        "fullName",  u.getFirstname() + " " + u.getLastname().toUpperCase()
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}
