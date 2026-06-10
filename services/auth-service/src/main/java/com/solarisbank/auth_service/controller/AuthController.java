package com.solarisbank.auth_service.controller;

import com.solarisbank.auth_service.dto.ChangePasswordRequest;
import com.solarisbank.auth_service.dto.ConfirmEmailChangeOtpRequest;
import com.solarisbank.auth_service.dto.ForgotPasswordRequest;
import com.solarisbank.auth_service.dto.LoginRequest;
import com.solarisbank.auth_service.dto.LoginResponse;
import com.solarisbank.auth_service.dto.OtpChallengeResponse;
import com.solarisbank.auth_service.dto.RegisterRequest;
import com.solarisbank.auth_service.dto.RequestEmailChangeRequest;
import com.solarisbank.auth_service.dto.ResendVerificationRequest;
import com.solarisbank.auth_service.dto.ResetPasswordRequest;
import com.solarisbank.auth_service.dto.VerifyOtpRequest;
import com.solarisbank.auth_service.exception.BusinessException;
import com.solarisbank.auth_service.model.User;
import com.solarisbank.auth_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Refresh token cookie lifetime matches JWT refresh-expiration setting.
     * Injected as a field so it stays outside @RequiredArgsConstructor.
     */
    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Account created successfully",
                        "userId", user.getUserId()
                ));
    }

    /**
     * Step 1 of login.
     * <ul>
     *   <li><b>CLIENT users</b>: returns {@code {status:"OTP_REQUIRED", sessionToken:"…"}}
     *       — the frontend navigates to /verify-otp.</li>
     *   <li><b>ADMIN users</b>: OTP is bypassed — returns the full
     *       {@code {accessToken, email, …}} directly and sets the HttpOnly
     *       refresh-token cookie, exactly like /verify-otp does for clients.</li>
     * </ul>
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Object result = authService.login(request);

        if (result instanceof LoginResponse loginResponse) {
            // ADMIN direct login — issue cookie + return JWT body
            ResponseCookie cookie = buildRefreshCookie(loginResponse.getRefreshToken());
            loginResponse.setRefreshToken(null);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(loginResponse);
        }

        // CLIENT — OTP challenge
        return ResponseEntity.ok(result);
    }

    /**
     * Step 2 of 2FA login: validates the 6-digit OTP against the stored challenge
     * and, on success, issues the JWT + sets the HttpOnly refresh-token cookie.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<LoginResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        LoginResponse response = authService.verifyOtp(request.getSessionToken(), request.getCode());

        ResponseCookie cookie = buildRefreshCookie(response.getRefreshToken());
        response.setRefreshToken(null);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    /**
     * Regenerates a fresh 6-digit code for an existing OTP session and resends it
     * by email.  Rate-limiting is handled at the API Gateway level.
     */
    @PostMapping("/resend-otp")
    public ResponseEntity<Map<String, String>> resendOtp(
            @RequestBody Map<String, String> body) {
        String sessionToken = body.get("sessionToken");
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new BusinessException("sessionToken is required", HttpStatus.BAD_REQUEST);
        }
        authService.resendOtp(sessionToken);
        return ResponseEntity.ok(Map.of("message", "A new code has been sent to your email."));
    }

    /**
     * Reads the refresh token from the HttpOnly cookie rather than from the request
     * body.  Issues a fresh token pair and rotates the cookie.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(value = "refreshToken", required = false) String rawRefreshToken) {

        if (rawRefreshToken == null) {
            throw new BusinessException("No refresh token cookie", HttpStatus.UNAUTHORIZED);
        }

        LoginResponse response = authService.refresh(rawRefreshToken);

        ResponseCookie cookie = buildRefreshCookie(response.getRefreshToken());

        // Strip from body — refresh token lives in the cookie only
        response.setRefreshToken(null);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    /**
     * Explicit logout revokes the refresh token in the DB and clears the cookie,
     * eliminating the previous 7-day residual validity window after logout.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = "refreshToken", required = false) String rawRefreshToken) {

        authService.logout(rawRefreshToken);

        // Expire the cookie immediately
        ResponseCookie clearCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    /**
     * Called by the frontend when the user clicks the link in the verification email.
     * The token is passed as a query parameter: GET /verify-email?token=UUID
     */
    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully. You can now log in."));
    }

    /**
     * Generates and sends a fresh verification email.
     * Rate-limiting is handled by the existing gateway RateLimitFilter.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Verification email sent."));
    }

    /**
     * Sends a password-reset link to the given address.
     * Always returns 200 — never reveals whether the address is registered.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(Map.of("message",
                "If this email is registered, a reset link has been sent."));
    }

    /**
     * Validates the reset token and updates the password.
     * 404 for unknown/used tokens, 410 for expired tokens.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
    }

    // ── Account settings (authenticated) ─────────────────────────────────────

    /**
     * Changes the authenticated user's password.
     * Requires the current password as proof of identity.
     * All active sessions are revoked; the user must re-login on every device.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Principal principal) {
        authService.changePassword(principal.getName(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password changed successfully. Please log in again."));
    }

    /**
     * Step 1 of email change: validates current password and new email,
     * then sends a 6-digit OTP to the user's CURRENT email address.
     */
    @PostMapping("/request-email-change")
    public ResponseEntity<Map<String, String>> requestEmailChange(
            @Valid @RequestBody RequestEmailChangeRequest request,
            Principal principal) {
        authService.requestEmailChange(principal.getName(), request.getNewEmail(), request.getCurrentPassword());
        return ResponseEntity.ok(Map.of("message", "A verification code has been sent to your current email address."));
    }

    /**
     * Step 2 of email change: validates the OTP from the current email,
     * then sends a verification link to the NEW email address.
     */
    @PostMapping("/confirm-email-change-otp")
    public ResponseEntity<Map<String, String>> confirmEmailChangeOtp(
            @Valid @RequestBody ConfirmEmailChangeOtpRequest request,
            Principal principal) {
        authService.confirmEmailChangeOtp(principal.getName(), request.getCode());
        return ResponseEntity.ok(Map.of("message", "Code verified. A confirmation link has been sent to your new email address."));
    }

    /**
     * Step 3 of email change: called when the user clicks the link in the new-email
     * verification message.  Updates the email, revokes all sessions.
     * Public endpoint — the token in the query param is the credential.
     */
    @GetMapping("/verify-new-email")
    public ResponseEntity<Map<String, String>> verifyNewEmail(@RequestParam String token) {
        authService.verifyNewEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email address updated successfully. Please log in with your new email."));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ResponseCookie buildRefreshCookie(String rawToken) {
        return ResponseCookie.from("refreshToken", rawToken)
                .httpOnly(true)
                .sameSite("Lax")
                // Scoped to auth endpoints — not sent with every API request
                .path("/api/v1/auth")
                .maxAge(refreshExpiration / 1000)
                .build();
    }
}
