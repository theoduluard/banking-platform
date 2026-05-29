package com.solarisbank.auth_service.service;

import com.solarisbank.auth_service.dto.LoginRequest;
import com.solarisbank.auth_service.dto.LoginResponse;
import com.solarisbank.auth_service.dto.RegisterRequest;
import com.solarisbank.auth_service.exception.BusinessException;
import com.solarisbank.auth_service.model.User;
import com.solarisbank.auth_service.repository.UserRepository;
import com.solarisbank.auth_service.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already in use", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstname(request.getFirstname())
                .lastname(request.getLastname())
                .role(User.Role.CLIENT)
                .build();

        return userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));

        return buildLoginResponse(user);
    }

    /**
     * Rotating refresh token — validates the incoming refresh token, then issues
     * a brand-new access + refresh pair.  The old refresh token is implicitly
     * invalidated because the client replaces it with the new one.
     */
    public LoginResponse refresh(String refreshToken) {
        String email;
        try {
            email = jwtService.extractEmail(refreshToken);
        } catch (Exception e) {
            log.warn("[Refresh] Malformed token — {}", e.getMessage());
            throw new BusinessException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }

        if (!jwtService.isTokenValid(refreshToken, email)) {
            throw new BusinessException("Refresh token expired or invalid", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.UNAUTHORIZED));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException("Account is disabled", HttpStatus.UNAUTHORIZED);
        }

        log.info("[Refresh] Issuing new token pair for user={}", email);
        return buildLoginResponse(user);
    }

    // ── shared helper ──────────────────────────────────────────────────────────

    private LoginResponse buildLoginResponse(User user) {
        return LoginResponse.builder()
                .accessToken(jwtService.generateAccessToken(
                        user.getEmail(),
                        user.getRole().name(),
                        user.getUserId()
                ))
                .refreshToken(jwtService.generateRefreshToken(user.getEmail()))
                .email(user.getEmail())
                .firstname(user.getFirstname())
                .lastname(user.getLastname())
                .role(user.getRole().name())
                .build();
    }
}
