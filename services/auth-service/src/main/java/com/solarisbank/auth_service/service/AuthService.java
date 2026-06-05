package com.solarisbank.auth_service.service;

import com.solarisbank.auth_service.dto.LoginRequest;
import com.solarisbank.auth_service.dto.LoginResponse;
import com.solarisbank.auth_service.dto.RegisterRequest;
import com.solarisbank.auth_service.exception.BusinessException;
import com.solarisbank.auth_service.model.RefreshToken;
import com.solarisbank.auth_service.model.User;
import com.solarisbank.auth_service.repository.RefreshTokenRepository;
import com.solarisbank.auth_service.repository.UserRepository;
import com.solarisbank.auth_service.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Refresh-expiration from application.properties (milliseconds).
     * Injected as a field (non-final) to stay outside the Lombok @RequiredArgsConstructor.
     */
    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already in use", HttpStatus.CONFLICT);
        }

        String token  = UUID.randomUUID().toString();

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstname(request.getFirstname())
                .lastname(request.getLastname())
                .role(User.Role.CLIENT)
                .emailVerified(false)
                .emailVerificationToken(token)
                .emailVerificationTokenExpiry(LocalDateTime.now().plusHours(24))
                .build();

        userRepository.save(user);
        emailService.sendVerificationEmail(user.getEmail(), user.getFirstname(), token);
        log.info("[Register] New user {} — verification email sent", user.getEmail());
        return user;
    }

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));

        // NULL = pre-existing user (backward compat) → allowed
        // FALSE = registered after email-verification was introduced → must verify
        if (Boolean.FALSE.equals(user.getEmailVerified())) {
            throw new BusinessException("Email not verified. Please check your inbox.", HttpStatus.FORBIDDEN);
        }

        return buildLoginResponse(user);
    }

    // ── Email verification ────────────────────────────────────────────────────

    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new BusinessException(
                        "Invalid or expired verification token.", HttpStatus.NOT_FOUND));

        if (user.getEmailVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BusinessException(
                    "Verification token has expired. Please request a new one.", HttpStatus.GONE);
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);
        userRepository.save(user);
        log.info("[Verify] Email verified for user {}", user.getEmail());
    }

    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BusinessException("Email is already verified.", HttpStatus.BAD_REQUEST);
        }

        String token = UUID.randomUUID().toString();
        user.setEmailVerificationToken(token);
        user.setEmailVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getFirstname(), token);
        log.info("[Verify] Resent verification email to {}", email);
    }

    // ── Password reset ────────────────────────────────────────────────────────

    /**
     * Generates a password-reset token and sends it by email.
     * Never reveals whether the address is registered (security).
     */
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setPasswordResetToken(token);
            user.setPasswordResetTokenExpiry(LocalDateTime.now().plusHours(1));
            userRepository.save(user);
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstname(), token);
            log.info("[Reset] Password reset email sent to {}", email);
        });
        // Log at info level even when user doesn't exist — do not distinguish in response
        log.info("[Reset] Password reset requested for {}", email);
    }

    /**
     * Validates the reset token, then updates the user's password.
     * 404 for unknown/used tokens, 410 for expired tokens.
     */
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new BusinessException(
                        "Invalid or already used reset token.", HttpStatus.NOT_FOUND));

        if (user.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BusinessException(
                    "Reset token has expired. Please request a new one.", HttpStatus.GONE);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        userRepository.save(user);
        log.info("[Reset] Password successfully reset for user {}", user.getEmail());
    }

    // ── Refresh token ────────────────────────────────────────────────────────

    /**
     * Validates a raw opaque refresh token against the DB hash store, rotates it
     * (deletes old entry, issues new one), and returns a fresh token pair.
     * The token is an opaque UUID string stored as a SHA-256 hash in DB.
     * The caller (AuthController) receives the raw token from the HttpOnly cookie,
     * passes it here, and puts the new cookie in the response.
     */
    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> {
                    log.warn("[Refresh] Unknown or already-rotated refresh token");
                    return new BusinessException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);
                });

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);   // clean up stale entry
            throw new BusinessException("Refresh token has expired", HttpStatus.UNAUTHORIZED);
        }

        User user = stored.getUser();
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException("Account is disabled", HttpStatus.UNAUTHORIZED);
        }

        // Rotate: remove the old token so it can never be reused
        refreshTokenRepository.delete(stored);

        log.info("[Refresh] Issuing new token pair for user={}", user.getEmail());
        return buildLoginResponse(user);
    }

    /**
     * Revokes the refresh token identified by the raw cookie value.
     * Idempotent: does nothing if the token is unknown or already revoked.
     * Explicit logout truly invalidates the session,
     * eliminating the 7-day residual validity window.
     */
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = sha256(rawRefreshToken);
        refreshTokenRepository.deleteByTokenHash(hash);
        log.info("[Logout] Refresh token revoked");
    }

    // ── Housekeeping ──────────────────────────────────────────────────────────

    /**
     * Deletes expired refresh tokens every day at 03:00.
     * Keeps the refresh_tokens table from growing indefinitely — tokens that have
     * passed their expiry are dead anyway (the expiry check in refresh() rejects them),
     * so they are safe to purge at any time.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("[Cleanup] Purged {} expired refresh token(s)", deleted);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /**
     * Builds the LoginResponse, issuing a fresh DB-backed opaque refresh token.
     * Called from login(), refresh(), and any other flow that starts a new session.
     */
    private LoginResponse buildLoginResponse(User user) {
        return LoginResponse.builder()
                .accessToken(jwtService.generateAccessToken(
                        user.getEmail(),
                        user.getRole().name(),
                        user.getUserId()
                ))
                // Opaque token persisted as SHA-256 hash;
                // the controller sets it as an HttpOnly cookie and strips it from the body.
                .refreshToken(issueRefreshToken(user))
                .email(user.getEmail())
                .firstname(user.getFirstname())
                .lastname(user.getLastname())
                .role(user.getRole().name())
                .build();
    }

    /**
     * Issues a new opaque refresh token:
     *  1. Generate a cryptographically random string (two UUIDs joined).
     *  2. Persist only its SHA-256 hash with an absolute expiry.
     *  3. Return the plaintext — it will be placed in an HttpOnly cookie by the controller.
     */
    private String issueRefreshToken(User user) {
        // Two random UUIDs give 256 bits of entropy (128 bits each)
        String rawToken = UUID.randomUUID() + "-" + UUID.randomUUID();
        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(sha256(rawToken))
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpiration / 1000))
                .build());
        return rawToken;
    }

    /** SHA-256 hex-digest of an arbitrary string. */
    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
