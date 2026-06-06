package com.solarisbank.auth_service.service;

import com.solarisbank.auth_service.dto.LoginRequest;
import com.solarisbank.auth_service.dto.LoginResponse;
import com.solarisbank.auth_service.dto.OtpChallengeResponse;
import com.solarisbank.auth_service.dto.RegisterRequest;
import com.solarisbank.auth_service.exception.BusinessException;
import com.solarisbank.auth_service.model.OtpChallenge;
import com.solarisbank.auth_service.model.RefreshToken;
import com.solarisbank.auth_service.model.User;
import com.solarisbank.auth_service.repository.EmailChangeRequestRepository;
import com.solarisbank.auth_service.repository.OtpChallengeRepository;
import com.solarisbank.auth_service.repository.RefreshTokenRepository;
import com.solarisbank.auth_service.repository.UserRepository;
import com.solarisbank.auth_service.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private EmailService emailService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private OtpChallengeRepository otpChallengeRepository;

    @Mock
    private EmailChangeRequestRepository emailChangeRequestRepository;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setEmail("john.doe@example.com");
        registerRequest.setPassword("Secret@123");
        registerRequest.setFirstname("John");
        registerRequest.setLastname("Doe");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("john.doe@example.com");
        loginRequest.setPassword("Secret@123");

        savedUser = User.builder()
                .userId(UUID.randomUUID())
                .email("john.doe@example.com")
                .firstname("John")
                .lastname("Doe")
                .password("encoded_password")
                .role(User.Role.CLIENT)
                .isActive(true)
                .build();
    }

    // ── register ───────────────────────────────────────────────────────────────

    @Test
    void register_shouldSaveAndReturnUser_whenEmailIsNew() {
        // Arrange
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        User result = authService.register(registerRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(result.getRole()).isEqualTo(User.Role.CLIENT);
        verify(passwordEncoder).encode("Secret@123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_shouldThrowConflict_whenEmailAlreadyExists() {
        // Arrange
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Email already in use");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void register_shouldEncodePassword_beforeSaving() {
        // Arrange
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("Secret@123")).thenReturn("hashed_value");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            assertThat(u.getPassword()).isEqualTo("hashed_value");
            return savedUser;
        });

        // Act
        authService.register(registerRequest);

        // Assert
        verify(passwordEncoder).encode("Secret@123");
    }

    // ── login (step 1) ────────────────────────────────────────────────────────

    @Test
    void login_shouldReturnOtpChallenge_whenCredentialsAreValid() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(savedUser));
        when(otpChallengeRepository.save(any(OtpChallenge.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OtpChallengeResponse response = authService.login(loginRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("OTP_REQUIRED");
        assertThat(response.getSessionToken()).isNotBlank();
        verify(otpChallengeRepository).deleteByUser(savedUser);
        verify(otpChallengeRepository).save(any(OtpChallenge.class));
        verify(emailService).sendOtpEmail(eq("john.doe@example.com"), eq("John"), anyString());
    }

    @Test
    void login_shouldCallAuthenticationManager_withCorrectCredentials() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(savedUser));
        when(otpChallengeRepository.save(any(OtpChallenge.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        authService.login(loginRequest);

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("john.doe@example.com", "Secret@123")
        );
    }

    @Test
    void login_shouldThrowException_whenAuthenticationFails() {
        // userRepository is called once for the lockout pre-check (returns empty → no lock)
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.empty());
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // BadCredentialsException is now caught and re-thrown as BusinessException
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid credentials");

        verify(otpChallengeRepository, never()).save(any());
    }

    // ── verifyOtp (step 2) ────────────────────────────────────────────────────

    @Test
    void verifyOtp_shouldReturnTokens_whenCodeIsCorrect() {
        String sessionToken = "test-session";
        String rawCode      = "123456";

        // Compute the hash the same way the service does (SHA-256)
        OtpChallenge challenge = OtpChallenge.builder()
                .sessionToken(sessionToken)
                .user(savedUser)
                .codeHash(sha256ForTest(rawCode))
                .attempts(0)
                .expiresAt(java.time.LocalDateTime.now().plusMinutes(10))
                .build();

        when(otpChallengeRepository.findBySessionToken(sessionToken))
                .thenReturn(Optional.of(challenge));
        when(jwtService.generateAccessToken(anyString(), anyString(), any(UUID.class)))
                .thenReturn("access_token");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LoginResponse response = authService.verifyOtp(sessionToken, rawCode);

        assertThat(response.getAccessToken()).isEqualTo("access_token");
        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
        verify(otpChallengeRepository).delete(challenge);
    }

    @Test
    void verifyOtp_shouldThrowUnauthorized_whenCodeIsWrong() {
        OtpChallenge challenge = OtpChallenge.builder()
                .sessionToken("s")
                .user(savedUser)
                .codeHash(sha256ForTest("999999"))
                .attempts(0)
                .expiresAt(java.time.LocalDateTime.now().plusMinutes(10))
                .build();

        when(otpChallengeRepository.findBySessionToken("s")).thenReturn(Optional.of(challenge));
        when(otpChallengeRepository.save(any(OtpChallenge.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> authService.verifyOtp("s", "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid code");

        verify(otpChallengeRepository, never()).delete(any(OtpChallenge.class));
    }

    @Test
    void verifyOtp_shouldDeleteChallenge_afterMaxAttempts() {
        OtpChallenge challenge = OtpChallenge.builder()
                .sessionToken("s")
                .user(savedUser)
                .codeHash(sha256ForTest("999999"))
                .attempts(2)      // already 2 failed → next failure triggers deletion
                .expiresAt(java.time.LocalDateTime.now().plusMinutes(10))
                .build();

        when(otpChallengeRepository.findBySessionToken("s")).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> authService.verifyOtp("s", "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Too many failed attempts");

        verify(otpChallengeRepository).delete(challenge);
    }

    @Test
    void verifyOtp_shouldThrowGone_whenChallengeIsExpired() {
        OtpChallenge challenge = OtpChallenge.builder()
                .sessionToken("s")
                .user(savedUser)
                .codeHash(sha256ForTest("123456"))
                .attempts(0)
                .expiresAt(java.time.LocalDateTime.now().minusMinutes(1))  // already expired
                .build();

        when(otpChallengeRepository.findBySessionToken("s")).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> authService.verifyOtp("s", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");

        verify(otpChallengeRepository).delete(challenge);
    }

    // ── login — additional edge cases ─────────────────────────────────────────

    @Test
    void login_shouldThrowForbidden_whenEmailIsNotVerified() {
        savedUser.setEmailVerified(false);
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(savedUser));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Email not verified");
    }

    @Test
    void login_shouldThrowUnauthorized_whenAccountIsDisabled() {
        savedUser.setIsActive(false);
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(savedUser));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account is disabled");
    }

    // ── resendOtp ─────────────────────────────────────────────────────────────

    @Test
    void resendOtp_shouldRegenerateCodeAndSendEmail_whenSessionIsValid() {
        OtpChallenge challenge = OtpChallenge.builder()
                .sessionToken("valid-session")
                .user(savedUser)
                .codeHash(sha256ForTest("123456"))
                .attempts(1)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(otpChallengeRepository.findBySessionToken("valid-session"))
                .thenReturn(Optional.of(challenge));
        when(otpChallengeRepository.save(any(OtpChallenge.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        authService.resendOtp("valid-session");

        verify(otpChallengeRepository).save(argThat(c ->
                c.getAttempts() == 0
                && c.getExpiresAt().isAfter(LocalDateTime.now())
        ));
        verify(emailService).sendOtpEmail(
                eq("john.doe@example.com"), eq("John"), anyString());
    }

    @Test
    void resendOtp_shouldThrowUnauthorized_whenSessionNotFound() {
        when(otpChallengeRepository.findBySessionToken("bad-session"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resendOtp("bad-session"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid or expired OTP session");
    }

    // ── verifyEmail ────────────────────────────────────────────────────────────

    @Test
    void verifyEmail_shouldActivateUser_whenTokenIsValid() {
        savedUser.setEmailVerified(false);
        savedUser.setEmailVerificationToken("valid-token");
        savedUser.setEmailVerificationTokenExpiry(LocalDateTime.now().plusHours(1));

        when(userRepository.findByEmailVerificationToken("valid-token"))
                .thenReturn(Optional.of(savedUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.verifyEmail("valid-token");

        verify(userRepository).save(argThat(u ->
                Boolean.TRUE.equals(u.getEmailVerified())
                && u.getEmailVerificationToken() == null
        ));
    }

    @Test
    void verifyEmail_shouldThrowNotFound_whenTokenIsInvalid() {
        when(userRepository.findByEmailVerificationToken("bad-token"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("bad-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid or expired verification token");
    }

    @Test
    void verifyEmail_shouldThrowGone_whenTokenIsExpired() {
        savedUser.setEmailVerificationToken("expired-token");
        savedUser.setEmailVerificationTokenExpiry(LocalDateTime.now().minusHours(1));

        when(userRepository.findByEmailVerificationToken("expired-token"))
                .thenReturn(Optional.of(savedUser));

        assertThatThrownBy(() -> authService.verifyEmail("expired-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Verification token has expired");
    }

    // ── resendVerification ─────────────────────────────────────────────────────

    @Test
    void resendVerification_shouldSendEmail_whenUserExistsAndNotVerified() {
        savedUser.setEmailVerified(false);
        when(userRepository.findByEmail("john.doe@example.com"))
                .thenReturn(Optional.of(savedUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.resendVerification("john.doe@example.com");

        verify(emailService).sendVerificationEmail(
                eq("john.doe@example.com"), eq("John"), anyString());
    }

    @Test
    void resendVerification_shouldDoNothing_whenEmailAlreadyVerified() {
        // The method now uses ifPresent + filter — already-verified users are silently skipped
        savedUser.setEmailVerified(true);
        when(userRepository.findByEmail("john.doe@example.com"))
                .thenReturn(Optional.of(savedUser));

        // Must not throw and must not send any email
        authService.resendVerification("john.doe@example.com");

        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resendVerification_shouldDoNothing_whenUserDoesNotExist() {
        // Unknown addresses are silently ignored to prevent email enumeration
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        // Must not throw and must not send any email
        authService.resendVerification("nobody@example.com");

        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
        verify(userRepository, never()).save(any());
    }

    // ── requestPasswordReset ──────────────────────────────────────────────────

    @Test
    void requestPasswordReset_shouldSendEmail_whenUserExists() {
        when(userRepository.findByEmail("john.doe@example.com"))
                .thenReturn(Optional.of(savedUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.requestPasswordReset("john.doe@example.com");

        verify(emailService).sendPasswordResetEmail(
                eq("john.doe@example.com"), eq("John"), anyString());
    }

    @Test
    void requestPasswordReset_shouldDoNothing_whenUserDoesNotExist() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        // No exception — security: don't reveal whether address is registered
        authService.requestPasswordReset("ghost@example.com");

        verifyNoInteractions(emailService);
        verify(userRepository, never()).save(any());
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Test
    void resetPassword_shouldUpdatePassword_whenTokenIsValid() {
        savedUser.setPasswordResetToken("reset-tok");
        savedUser.setPasswordResetTokenExpiry(LocalDateTime.now().plusHours(1));

        when(userRepository.findByPasswordResetToken("reset-tok"))
                .thenReturn(Optional.of(savedUser));
        when(passwordEncoder.encode("NewSecret@99")).thenReturn("new_encoded");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.resetPassword("reset-tok", "NewSecret@99");

        verify(userRepository).save(argThat(u ->
                "new_encoded".equals(u.getPassword())
                && u.getPasswordResetToken() == null
        ));
    }

    @Test
    void resetPassword_shouldThrowNotFound_whenTokenIsInvalid() {
        when(userRepository.findByPasswordResetToken("unknown-tok"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("unknown-tok", "pass"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid or already used reset token");
    }

    @Test
    void resetPassword_shouldThrowGone_whenTokenIsExpired() {
        savedUser.setPasswordResetToken("exp-tok");
        savedUser.setPasswordResetTokenExpiry(LocalDateTime.now().minusMinutes(1));

        when(userRepository.findByPasswordResetToken("exp-tok"))
                .thenReturn(Optional.of(savedUser));

        assertThatThrownBy(() -> authService.resetPassword("exp-tok", "pass"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Reset token has expired");
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    void refresh_shouldReturnNewTokenPair_whenRefreshTokenIsValid() {
        String rawToken = "raw-refresh-token";
        RefreshToken stored = RefreshToken.builder()
                .tokenHash(sha256ForTest(rawToken))
                .user(savedUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(refreshTokenRepository.findByTokenHash(sha256ForTest(rawToken)))
                .thenReturn(Optional.of(stored));
        when(jwtService.generateAccessToken(anyString(), anyString(), any(UUID.class)))
                .thenReturn("new_access_token");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginResponse response = authService.refresh(rawToken);

        assertThat(response.getAccessToken()).isEqualTo("new_access_token");
        verify(refreshTokenRepository).delete(stored);
    }

    @Test
    void refresh_shouldThrowUnauthorized_whenTokenIsInvalid() {
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("unknown-refresh-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    @Test
    void refresh_shouldThrowUnauthorized_whenTokenIsExpired() {
        String rawToken = "expired-token";
        RefreshToken expired = RefreshToken.builder()
                .tokenHash(sha256ForTest(rawToken))
                .user(savedUser)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(refreshTokenRepository.findByTokenHash(sha256ForTest(rawToken)))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(rawToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");

        verify(refreshTokenRepository).delete(expired);
    }

    @Test
    void refresh_shouldThrowUnauthorized_whenAccountIsDisabled() {
        savedUser.setIsActive(false);
        String rawToken = "some-token";
        RefreshToken stored = RefreshToken.builder()
                .tokenHash(sha256ForTest(rawToken))
                .user(savedUser)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(refreshTokenRepository.findByTokenHash(sha256ForTest(rawToken)))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(rawToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account is disabled");
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_shouldDeleteToken_whenTokenIsProvided() {
        authService.logout("some-refresh-token");

        verify(refreshTokenRepository).deleteByTokenHash(sha256ForTest("some-refresh-token"));
    }

    @Test
    void logout_shouldDoNothing_whenTokenIsNull() {
        authService.logout(null);

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void logout_shouldDoNothing_whenTokenIsBlank() {
        authService.logout("   ");

        verifyNoInteractions(refreshTokenRepository);
    }

    // ── cleanupExpiredTokens ──────────────────────────────────────────────────

    @Test
    void cleanupExpiredTokens_shouldDeleteExpiredRefreshTokensAndOtpChallenges() {
        when(refreshTokenRepository.deleteByExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(3);
        when(otpChallengeRepository.deleteByExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(2);
        when(emailChangeRequestRepository.deleteByExpiresAtBeforeAndCompletedAtIsNull(any(LocalDateTime.class)))
                .thenReturn(1);

        authService.cleanupExpiredTokens();

        verify(refreshTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
        verify(otpChallengeRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
        verify(emailChangeRequestRepository).deleteByExpiresAtBeforeAndCompletedAtIsNull(any(LocalDateTime.class));
    }

    // ── Helper — mirrors AuthService.sha256() ─────────────────────────────────

    private String sha256ForTest(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
