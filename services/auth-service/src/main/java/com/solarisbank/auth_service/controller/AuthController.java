package com.solarisbank.auth_service.controller;

import com.solarisbank.auth_service.dto.ForgotPasswordRequest;
import com.solarisbank.auth_service.dto.LoginRequest;
import com.solarisbank.auth_service.dto.LoginResponse;
import com.solarisbank.auth_service.dto.RegisterRequest;
import com.solarisbank.auth_service.dto.ResendVerificationRequest;
import com.solarisbank.auth_service.dto.ResetPasswordRequest;
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
     * The refresh token is returned as an HttpOnly, SameSite=Lax cookie instead of
     * a JSON body field.  This prevents JavaScript (XSS) from accessing or
     * exfiltrating the refresh token.
     * The role value is included in the response body so the frontend can store it
     * in sessionStorage and avoid unsafe client-side JWT decoding.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);

        ResponseCookie cookie = buildRefreshCookie(response.getRefreshToken());

        // Strip the refresh token from the JSON body — it lives in the cookie only
        response.setRefreshToken(null);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
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
