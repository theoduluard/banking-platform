package com.solarisbank.auth_service.service;

import com.solarisbank.auth_service.dto.LoginRequest;
import com.solarisbank.auth_service.dto.LoginResponse;
import com.solarisbank.auth_service.dto.RegisterRequest;
import com.solarisbank.auth_service.exception.BusinessException;
import com.solarisbank.auth_service.model.User;
import com.solarisbank.auth_service.repository.UserRepository;
import com.solarisbank.auth_service.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
