package com.solarisbank.auth_service.service;

import com.solarisbank.auth_service.dto.LoginRequest;
import com.solarisbank.auth_service.dto.LoginResponse;
import com.solarisbank.auth_service.dto.OtpChallengeResponse;
import com.solarisbank.auth_service.dto.RegisterRequest;
import com.solarisbank.auth_service.exception.BusinessException;
import com.solarisbank.auth_service.model.EmailChangeRequest;
import com.solarisbank.auth_service.model.OtpChallenge;
import com.solarisbank.auth_service.model.RefreshToken;
import com.solarisbank.auth_service.model.User;
import com.solarisbank.auth_service.repository.EmailChangeRequestRepository;
import com.solarisbank.auth_service.repository.OtpChallengeRepository;
import com.solarisbank.auth_service.repository.RefreshTokenRepository;
import com.solarisbank.auth_service.repository.UserRepository;
import com.solarisbank.auth_service.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository                userRepository;
    private final PasswordEncoder               passwordEncoder;
    private final AuthenticationManager         authenticationManager;
    private final JwtService                    jwtService;
    private final EmailService                  emailService;
    private final RefreshTokenRepository        refreshTokenRepository;
    private final OtpChallengeRepository        otpChallengeRepository;
    private final EmailChangeRequestRepository  emailChangeRequestRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    /** OTP validity window in minutes. */
    private static final int OTP_VALIDITY_MINUTES = 10;

    /** Maximum wrong-code attempts before the challenge is voided. */
    private static final int OTP_MAX_ATTEMPTS = 3;

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
        log.info("[Register] New user id={} — verification email sent", user.getUserId());
        return user;
    }

    /**
     * Step 1 of login: validates credentials, generates a 6-digit OTP, sends it by
     * email, and returns an opaque session token the frontend stores until step 2.
     *
     * <p>Account lockout thresholds (progressive back-off):
     * <ul>
     *   <li>≥ 5 consecutive failures → locked for 30 seconds</li>
     *   <li>≥ 10 consecutive failures → locked for 5 minutes</li>
     *   <li>≥ 15 consecutive failures → locked for 1 hour</li>
     * </ul>
     * The counter resets to 0 on the first successful authentication.
     */
    /**
     * Returns either an {@link OtpChallengeResponse} (CLIENT users — OTP required)
     * or a {@link LoginResponse} (ADMIN users — OTP bypassed, JWT issued directly).
     * The controller inspects the runtime type to decide whether to set the cookie.
     */
    @Transactional
    public Object login(LoginRequest request) {
        // Look up the user first so we can check / update the lockout state.
        // We intentionally do this before calling authenticationManager so that a
        // locked account short-circuits immediately without a DB password-hash comparison.
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        // Enforce lockout before attempting any authentication
        if (user != null
                && user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long remaining = java.time.Duration.between(LocalDateTime.now(), user.getLockedUntil()).toSeconds();
            throw new BusinessException(
                    "Account temporarily locked. Try again in " + remaining + " second(s).",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (DisabledException e) {
            throw new BusinessException("Account is disabled", HttpStatus.UNAUTHORIZED);
        } catch (BadCredentialsException e) {
            if (user != null) {
                int attempts = user.getFailedLoginAttempts() + 1;
                user.setFailedLoginAttempts(attempts);
                user.setLockedUntil(computeLockout(attempts));
                userRepository.save(user);
            }
            throw new BusinessException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        // Authentication succeeded — load fresh user entity if we had no record above
        if (user == null) {
            user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));
        }

        // Reset the failure counter on successful authentication
        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        // NULL = pre-existing user (backward compat) → allowed
        // FALSE = registered after email-verification was introduced → must verify
        if (Boolean.FALSE.equals(user.getEmailVerified())) {
            throw new BusinessException("Email not verified. Please check your inbox.", HttpStatus.FORBIDDEN);
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException("Account is disabled", HttpStatus.UNAUTHORIZED);
        }

        // Admin accounts skip the OTP step entirely — password is sufficient.
        // OTP adds friction without extra security for a back-office account
        // whose inbox is already protected by the admin's own credentials.
        if (user.getRole() == User.Role.ADMIN) {
            log.info("[Login] Admin user id={} — OTP bypassed, JWT issued directly", user.getUserId());
            return buildLoginResponse(user);
        }

        // Replace any previous pending challenge for this user
        otpChallengeRepository.deleteByUser(user);

        String rawCode      = String.format("%06d", secureRandom.nextInt(1_000_000));
        String sessionToken = UUID.randomUUID().toString();

        otpChallengeRepository.save(OtpChallenge.builder()
                .sessionToken(sessionToken)
                .user(user)
                .codeHash(sha256(rawCode))
                .attempts(0)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES))
                .build());

        emailService.sendOtpEmail(user.getEmail(), user.getFirstname(), rawCode);
        log.info("[Login] OTP challenge issued for user id={}", user.getUserId());

        return new OtpChallengeResponse(sessionToken);
    }

    /**
     * Returns the lock-expiry instant for a given failed-attempt count,
     * or {@code null} if the threshold has not yet been reached.
     */
    private LocalDateTime computeLockout(int failedAttempts) {
        if (failedAttempts >= 15) return LocalDateTime.now().plusHours(1);
        if (failedAttempts >= 10) return LocalDateTime.now().plusMinutes(5);
        if (failedAttempts >= 5)  return LocalDateTime.now().plusSeconds(30);
        return null;
    }

    /**
     * Step 2 of login: validates the OTP code against the stored challenge and,
     * on success, issues the JWT + refresh token pair.
     * Tracks failed attempts and voids the challenge after {@value OTP_MAX_ATTEMPTS} failures.
     */
    @Transactional
    public LoginResponse verifyOtp(String sessionToken, String code) {
        OtpChallenge challenge = otpChallengeRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new BusinessException(
                        "Invalid or expired OTP session. Please log in again.", HttpStatus.UNAUTHORIZED));

        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            otpChallengeRepository.delete(challenge);
            throw new BusinessException(
                    "OTP code has expired. Please log in again.", HttpStatus.GONE);
        }

        if (!challenge.getCodeHash().equals(sha256(code))) {
            int remaining = OTP_MAX_ATTEMPTS - challenge.getAttempts() - 1;
            if (remaining <= 0) {
                otpChallengeRepository.delete(challenge);
                throw new BusinessException(
                        "Too many failed attempts. Please log in again.", HttpStatus.UNAUTHORIZED);
            }
            challenge.setAttempts(challenge.getAttempts() + 1);
            otpChallengeRepository.save(challenge);
            throw new BusinessException(
                    "Invalid code. " + remaining + " attempt(s) remaining.", HttpStatus.UNAUTHORIZED);
        }

        User user = challenge.getUser();
        otpChallengeRepository.delete(challenge);

        log.info("[OTP] Verified for user id={}", user.getUserId());
        return buildLoginResponse(user);
    }

    /**
     * Regenerates a fresh 6-digit code for an existing OTP session.
     * Resets the expiry and the attempt counter.
     */
    @Transactional
    public void resendOtp(String sessionToken) {
        OtpChallenge challenge = otpChallengeRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new BusinessException(
                        "Invalid or expired OTP session. Please log in again.", HttpStatus.UNAUTHORIZED));

        String rawCode = String.format("%06d", secureRandom.nextInt(1_000_000));
        challenge.setCodeHash(sha256(rawCode));
        challenge.setAttempts(0);
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES));
        otpChallengeRepository.save(challenge);

        emailService.sendOtpEmail(
                challenge.getUser().getEmail(),
                challenge.getUser().getFirstname(),
                rawCode);
        log.info("[OTP] Code resent for user id={}", challenge.getUser().getUserId());
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
        log.info("[Verify] Email verified for user id={}", user.getUserId());
    }

    /**
     * Re-sends the email-verification link.
     * Always returns silently — never reveals whether the address is registered
     * or whether it is already verified (prevents email enumeration attacks).
     */
    public void resendVerification(String email) {
        userRepository.findByEmail(email)
                .filter(u -> !Boolean.TRUE.equals(u.getEmailVerified()))
                .ifPresent(u -> {
                    String token = UUID.randomUUID().toString();
                    u.setEmailVerificationToken(token);
                    u.setEmailVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
                    userRepository.save(u);
                    emailService.sendVerificationEmail(u.getEmail(), u.getFirstname(), token);
                    log.info("[Verify] Resent verification email to user id={}", u.getUserId());
                });
        // Intentionally no error thrown — caller always sees HTTP 200
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
            log.info("[Reset] Password reset email sent to user id={}", user.getUserId());
        });
        // Always log at info level — do not distinguish whether the address was found
        log.info("[Reset] Password reset requested");
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
        log.info("[Reset] Password successfully reset for user id={}", user.getUserId());
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

        log.info("[Refresh] Issuing new token pair for user id={}", user.getUserId());
        return buildLoginResponse(user);
    }

    /**
     * Revokes the refresh token identified by the raw cookie value.
     * Idempotent: does nothing if the token is unknown or already revoked.
     * Explicit logout truly invalidates the session,
     * eliminating the 7-day residual validity window.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = sha256(rawRefreshToken);
        refreshTokenRepository.deleteByTokenHash(hash);
        log.info("[Logout] Refresh token revoked");
    }

    // ── Password change ───────────────────────────────────────────────────────

    /**
     * Changes the user's password after verifying the current one.
     * Revokes all active refresh tokens (forces re-login on all devices).
     * Sends an email notification to confirm the change.
     */
    @Transactional
    public void changePassword(String userEmail, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException("Current password is incorrect", HttpStatus.UNAUTHORIZED);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Revoke all sessions — the user must re-login on every device
        refreshTokenRepository.deleteByUser(user);

        emailService.sendPasswordChangedEmail(user.getEmail(), user.getFirstname());
        log.info("[ChangePassword] Password changed for user id={}", user.getUserId());
    }

    // ── Email change (3-step) ────────────────────────────────────────────────

    /**
     * Step 1 — initiates an email-change request.
     * Verifies the current password, checks the new address is available,
     * generates a 6-digit OTP and sends it to the user's <em>current</em> email.
     */
    @Transactional
    public void requestEmailChange(String userEmail, String newEmail, String currentPassword) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException("Current password is incorrect", HttpStatus.UNAUTHORIZED);
        }

        if (userEmail.equalsIgnoreCase(newEmail)) {
            throw new BusinessException("New email must be different from the current one", HttpStatus.BAD_REQUEST);
        }

        if (userRepository.existsByEmail(newEmail)) {
            throw new BusinessException("Email already in use", HttpStatus.CONFLICT);
        }

        // Replace any previous pending request
        emailChangeRequestRepository.deleteByUser(user);

        String rawCode = String.format("%06d", secureRandom.nextInt(1_000_000));

        emailChangeRequestRepository.save(EmailChangeRequest.builder()
                .user(user)
                .newEmail(newEmail)
                .otpCodeHash(sha256(rawCode))
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES))
                .build());

        emailService.sendEmailChangeOtpEmail(user.getEmail(), user.getFirstname(), rawCode);
        log.info("[EmailChange] Step 1 — OTP sent to current email for user id={}", user.getUserId());
    }

    /**
     * Step 2 — validates the OTP sent to the current email.
     * On success, generates a single-use verification token and sends it (as a
     * link) to the <em>new</em> email address.
     */
    @Transactional
    public void confirmEmailChangeOtp(String userEmail, String code) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));

        EmailChangeRequest request = emailChangeRequestRepository.findPendingOtpByUser(user)
                .orElseThrow(() -> new BusinessException(
                        "No pending email-change request. Please start again.", HttpStatus.NOT_FOUND));

        if (request.getExpiresAt().isBefore(LocalDateTime.now())) {
            emailChangeRequestRepository.delete(request);
            throw new BusinessException("OTP has expired. Please start again.", HttpStatus.GONE);
        }

        if (!request.getOtpCodeHash().equals(sha256(code))) {
            throw new BusinessException("Invalid code. Please check your email.", HttpStatus.UNAUTHORIZED);
        }

        // OTP validated — advance to step 2
        String verifyToken = UUID.randomUUID().toString();
        request.setOtpVerifiedAt(LocalDateTime.now());
        request.setVerifyToken(verifyToken);
        request.setExpiresAt(LocalDateTime.now().plusHours(1));   // 1 h to click the link
        emailChangeRequestRepository.save(request);

        emailService.sendNewEmailVerificationEmail(request.getNewEmail(), user.getFirstname(), verifyToken);
        log.info("[EmailChange] Step 2 — verification link sent to new email for user id={}", user.getUserId());
    }

    /**
     * Step 3 — called when the user clicks the link in the new-email verification message.
     * Updates the email, revokes all sessions (fresh login required), and notifies
     * the old address.
     */
    @Transactional
    public void verifyNewEmail(String token) {
        EmailChangeRequest request = emailChangeRequestRepository.findByVerifyToken(token)
                .orElseThrow(() -> new BusinessException(
                        "Invalid or expired verification link.", HttpStatus.NOT_FOUND));

        if (request.getOtpVerifiedAt() == null) {
            throw new BusinessException("Invalid request state.", HttpStatus.BAD_REQUEST);
        }

        if (request.getExpiresAt().isBefore(LocalDateTime.now())) {
            emailChangeRequestRepository.delete(request);
            throw new BusinessException("Link has expired. Please start again.", HttpStatus.GONE);
        }

        User user     = request.getUser();
        String oldEmail = user.getEmail();
        String newEmail = request.getNewEmail();

        // Guard against a race: the new email might have been registered since step 1
        if (userRepository.existsByEmail(newEmail)) {
            emailChangeRequestRepository.delete(request);
            throw new BusinessException("Email already in use. Please start again.", HttpStatus.CONFLICT);
        }

        user.setEmail(newEmail);
        userRepository.save(user);

        request.setCompletedAt(LocalDateTime.now());
        emailChangeRequestRepository.save(request);

        // All sessions are tied to the old email — revoke them so the user re-logs in
        refreshTokenRepository.deleteByUser(user);

        emailService.sendEmailChangedNotificationEmail(oldEmail, user.getFirstname(), newEmail);
        log.info("[EmailChange] Step 3 — email changed from {} to {} for user id={}", oldEmail, newEmail, user.getUserId());
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
        int deletedRefresh    = refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        int deletedOtp        = otpChallengeRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        int deletedEmailChg   = emailChangeRequestRepository
                .deleteByExpiresAtBeforeAndCompletedAtIsNull(LocalDateTime.now());
        log.info("[Cleanup] Purged {} refresh token(s), {} OTP challenge(s), {} email-change request(s)",
                deletedRefresh, deletedOtp, deletedEmailChg);
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
