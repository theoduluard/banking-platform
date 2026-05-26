package com.solarisbank.auth_service.service;

import com.solarisbank.auth_service.dto.LoginRequest;
import com.solarisbank.auth_service.dto.LoginResponse;
import com.solarisbank.auth_service.dto.RegisterRequest;
import com.solarisbank.auth_service.exception.BusinessException;
import com.solarisbank.auth_service.model.User;
import com.solarisbank.auth_service.repository.UserRepository;
import com.solarisbank.auth_service.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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

    // ── login ──────────────────────────────────────────────────────────────────

    @Test
    void login_shouldReturnTokens_whenCredentialsAreValid() {
        // Arrange
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(savedUser));
        when(jwtService.generateAccessToken(anyString(), anyString(), any(UUID.class)))
                .thenReturn("access_token");
        when(jwtService.generateRefreshToken(anyString())).thenReturn("refresh_token");

        // Act
        LoginResponse response = authService.login(loginRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access_token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh_token");
        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(response.getFirstname()).isEqualTo("John");
        assertThat(response.getLastname()).isEqualTo("Doe");
        assertThat(response.getRole()).isEqualTo("CLIENT");
    }

    @Test
    void login_shouldCallAuthenticationManager_withCorrectCredentials() {
        // Arrange
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(savedUser));
        when(jwtService.generateAccessToken(anyString(), anyString(), any())).thenReturn("token");
        when(jwtService.generateRefreshToken(anyString())).thenReturn("refresh");

        // Act
        authService.login(loginRequest);

        // Assert
        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("john.doe@example.com", "Secret@123")
        );
    }

    @Test
    void login_shouldThrowException_whenAuthenticationFails() {
        // Arrange
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // Act & Assert
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByEmail(anyString());
        verify(jwtService, never()).generateAccessToken(anyString(), anyString(), any());
    }

    @Test
    void login_shouldPassCorrectClaimsToJwtService() {
        // Arrange
        UUID userId = savedUser.getUserId();
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(savedUser));
        when(jwtService.generateAccessToken(anyString(), anyString(), any())).thenReturn("token");
        when(jwtService.generateRefreshToken(anyString())).thenReturn("refresh");

        // Act
        authService.login(loginRequest);

        // Assert
        verify(jwtService).generateAccessToken("john.doe@example.com", "CLIENT", userId);
        verify(jwtService).generateRefreshToken("john.doe@example.com");
    }
}
