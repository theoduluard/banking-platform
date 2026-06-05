package com.solarisbank.auth_service.service;

import com.solarisbank.auth_service.dto.LoginRequest;
import com.solarisbank.auth_service.dto.LoginResponse;
import com.solarisbank.auth_service.dto.OtpChallengeResponse;
import com.solarisbank.auth_service.dto.RegisterRequest;
import com.solarisbank.auth_service.exception.BusinessException;
import com.solarisbank.auth_service.model.OtpChallenge;
import com.solarisbank.auth_service.model.RefreshToken;
import com.solarisbank.auth_service.model.User;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByEmail(anyString());
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
