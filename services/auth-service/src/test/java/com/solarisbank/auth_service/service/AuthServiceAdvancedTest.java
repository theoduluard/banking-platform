package com.solarisbank.auth_service.service;

import com.solarisbank.auth_service.dto.LoginRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceAdvancedTest {

    @Mock private UserRepository               userRepository;
    @Mock private PasswordEncoder              passwordEncoder;
    @Mock private AuthenticationManager        authenticationManager;
    @Mock private JwtService                   jwtService;
    @Mock private EmailService                 emailService;
    @Mock private RefreshTokenRepository       refreshTokenRepository;
    @Mock private OtpChallengeRepository       otpChallengeRepository;
    @Mock private EmailChangeRequestRepository emailChangeRequestRepository;

    @InjectMocks
    private AuthService authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .userId(UUID.randomUUID())
                .email("alice@example.com")
                .password("encoded_pwd")
                .firstname("Alice")
                .lastname("Test")
                .role(User.Role.CLIENT)
                .isActive(true)
                .emailVerified(true)
                .failedLoginAttempts(0)
                .build();
    }

    // ── Login lockout ──────────────────────────────────────────────────────────

    @Test
    void login_shouldIncrementFailedAttempts_onBadCredentials() {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@example.com");
        req.setPassword("wrong");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        doThrow(new BadCredentialsException("bad"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void login_shouldLockFor30s_afterFiveFailedAttempts() {
        activeUser.setFailedLoginAttempts(4); // will become 5
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@example.com");
        req.setPassword("wrong");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        doThrow(new BadCredentialsException("bad")).when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(saved.getLockedUntil()).isNotNull();
        assertThat(saved.getLockedUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    void login_shouldRejectLocked_accountBeforeLockExpiry() {
        activeUser.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@example.com");
        req.setPassword("any");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("locked");

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_shouldResetFailedAttempts_onSuccessfulLogin() {
        activeUser.setFailedLoginAttempts(3);
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@example.com");
        req.setPassword("correct");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(otpChallengeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.login(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isZero();
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    void login_shouldLockFor5Minutes_afterTenFailedAttempts() {
        activeUser.setFailedLoginAttempts(9);
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@example.com");
        req.setPassword("wrong");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        doThrow(new BadCredentialsException("bad")).when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(req)).isInstanceOf(BusinessException.class);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(4));
    }

    @Test
    void login_shouldLockFor1Hour_afterFifteenFailedAttempts() {
        activeUser.setFailedLoginAttempts(14);
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@example.com");
        req.setPassword("wrong");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        doThrow(new BadCredentialsException("bad")).when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(req)).isInstanceOf(BusinessException.class);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(59));
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    void changePassword_shouldUpdatePassword_andRevokeAllSessions() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("oldPass", "encoded_pwd")).thenReturn(true);
        when(passwordEncoder.encode("newPass123")).thenReturn("new_encoded");

        authService.changePassword("alice@example.com", "oldPass", "newPass123");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("new_encoded");
        verify(refreshTokenRepository).deleteByUser(activeUser);
        verify(emailService).sendPasswordChangedEmail(eq("alice@example.com"), eq("Alice"));
    }

    @Test
    void changePassword_shouldThrow_whenCurrentPasswordIsWrong() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", "encoded_pwd")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword("alice@example.com", "wrong", "newPass"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Current password is incorrect");

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_shouldThrowNotFound_whenUserDoesNotExist() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword("nobody@example.com", "x", "y"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("User not found");
    }

    // ── requestEmailChange (step 1) ───────────────────────────────────────────

    @Test
    void requestEmailChange_shouldSendOtpToCurrentEmail() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("pwd", "encoded_pwd")).thenReturn(true);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(emailChangeRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.requestEmailChange("alice@example.com", "new@example.com", "pwd");

        verify(emailChangeRequestRepository).deleteByUser(activeUser);
        verify(emailChangeRequestRepository).save(any());
        verify(emailService).sendEmailChangeOtpEmail(eq("alice@example.com"), eq("Alice"), anyString());
    }

    @Test
    void requestEmailChange_shouldThrow_whenCurrentPasswordIsWrong() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", "encoded_pwd")).thenReturn(false);

        assertThatThrownBy(() ->
                authService.requestEmailChange("alice@example.com", "new@example.com", "wrong"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    void requestEmailChange_shouldThrow_whenNewEmailSameAsCurrent() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("pwd", "encoded_pwd")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.requestEmailChange("alice@example.com", "alice@example.com", "pwd"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("different");
    }

    @Test
    void requestEmailChange_shouldThrow_whenNewEmailAlreadyInUse() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("pwd", "encoded_pwd")).thenReturn(true);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.requestEmailChange("alice@example.com", "taken@example.com", "pwd"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already in use");
    }

    // ── confirmEmailChangeOtp (step 2) ────────────────────────────────────────

    @Test
    void confirmEmailChangeOtp_validCode_shouldAdvanceToStep2() throws Exception {
        // Build hash manually using same algo as AuthService (SHA-256 hex)
        String rawCode = "123456";
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = md.digest(rawCode.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) sb.append(String.format("%02x", b));
        String hash = sb.toString();

        EmailChangeRequest request = EmailChangeRequest.builder()
                .user(activeUser)
                .newEmail("new@example.com")
                .otpCodeHash(hash)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(emailChangeRequestRepository.findPendingOtpByUser(activeUser))
                .thenReturn(Optional.of(request));
        when(emailChangeRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.confirmEmailChangeOtp("alice@example.com", rawCode);

        verify(emailChangeRequestRepository).save(any());
        verify(emailService).sendNewEmailVerificationEmail(
                eq("new@example.com"), eq("Alice"), anyString());
    }

    @Test
    void confirmEmailChangeOtp_shouldThrow_whenCodeIsWrong() {
        EmailChangeRequest request = EmailChangeRequest.builder()
                .user(activeUser)
                .newEmail("new@example.com")
                .otpCodeHash("some-hash")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(emailChangeRequestRepository.findPendingOtpByUser(activeUser))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() ->
                authService.confirmEmailChangeOtp("alice@example.com", "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid code");
    }

    @Test
    void confirmEmailChangeOtp_shouldThrow_whenRequestIsExpired() {
        EmailChangeRequest request = EmailChangeRequest.builder()
                .user(activeUser)
                .newEmail("new@example.com")
                .otpCodeHash("hash")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(emailChangeRequestRepository.findPendingOtpByUser(activeUser))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() ->
                authService.confirmEmailChangeOtp("alice@example.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");

        verify(emailChangeRequestRepository).delete(request);
    }

    @Test
    void confirmEmailChangeOtp_shouldThrow_whenNoPendingRequest() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(emailChangeRequestRepository.findPendingOtpByUser(activeUser))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.confirmEmailChangeOtp("alice@example.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No pending email-change");
    }

    // ── verifyNewEmail (step 3) ───────────────────────────────────────────────

    @Test
    void verifyNewEmail_validToken_shouldUpdateEmail_andRevokeSessions() {
        EmailChangeRequest request = EmailChangeRequest.builder()
                .user(activeUser)
                .newEmail("new@example.com")
                .verifyToken("verify-tok")
                .otpVerifiedAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(emailChangeRequestRepository.findByVerifyToken("verify-tok"))
                .thenReturn(Optional.of(request));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(emailChangeRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.verifyNewEmail("verify-tok");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("new@example.com");
        verify(refreshTokenRepository).deleteByUser(activeUser);
        verify(emailService).sendEmailChangedNotificationEmail(
                eq("alice@example.com"), eq("Alice"), eq("new@example.com"));
    }

    @Test
    void verifyNewEmail_shouldThrow_whenTokenNotFound() {
        when(emailChangeRequestRepository.findByVerifyToken("bad-tok"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyNewEmail("bad-tok"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void verifyNewEmail_shouldThrow_whenLinkExpired() {
        EmailChangeRequest request = EmailChangeRequest.builder()
                .user(activeUser)
                .newEmail("new@example.com")
                .verifyToken("tok")
                .otpVerifiedAt(LocalDateTime.now().minusHours(2))
                .expiresAt(LocalDateTime.now().minusMinutes(30))
                .build();

        when(emailChangeRequestRepository.findByVerifyToken("tok"))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> authService.verifyNewEmail("tok"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");

        verify(emailChangeRequestRepository).delete(request);
    }

    @Test
    void verifyNewEmail_shouldThrow_whenOtpNotVerifiedYet() {
        EmailChangeRequest request = EmailChangeRequest.builder()
                .user(activeUser)
                .newEmail("new@example.com")
                .verifyToken("tok")
                .otpVerifiedAt(null)  // step 2 not completed
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(emailChangeRequestRepository.findByVerifyToken("tok"))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> authService.verifyNewEmail("tok"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid request state");
    }

    @Test
    void verifyNewEmail_shouldThrow_whenNewEmailAlreadyTaken() {
        EmailChangeRequest request = EmailChangeRequest.builder()
                .user(activeUser)
                .newEmail("taken@example.com")
                .verifyToken("tok")
                .otpVerifiedAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(emailChangeRequestRepository.findByVerifyToken("tok"))
                .thenReturn(Optional.of(request));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.verifyNewEmail("tok"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already in use");

        verify(emailChangeRequestRepository).delete(request);
    }
}
