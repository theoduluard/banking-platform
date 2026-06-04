package com.solarisbank.auth_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.auth_service.config.SecurityConfig;
import com.solarisbank.auth_service.dto.LoginRequest;
import com.solarisbank.auth_service.dto.LoginResponse;
import com.solarisbank.auth_service.dto.RegisterRequest;
import com.solarisbank.auth_service.exception.BusinessException;
import com.solarisbank.auth_service.model.User;
import com.solarisbank.auth_service.security.JwtAuthFilter;
import com.solarisbank.auth_service.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * In Spring Boot 4.x, @WebMvcTest does NOT auto-include security by default —
 * only the standard WebMVC slices are loaded (MessageSource, WebMvc, Validation...).
 * We still need to exclude our custom SecurityConfig and JwtAuthFilter so Spring
 * doesn't try to instantiate their dependencies (UserRepository, JwtService).
 */
@WebMvcTest(
        value = AuthController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
        }
)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Instantiated directly — avoids Spring bean lookup issues in the WebMvcTest slice
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;
    private User registeredUser;
    private LoginResponse loginResponse;

    @BeforeEach
    void setUp() {
        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setEmail("john.doe@example.com");
        validRegisterRequest.setPassword("Secret@123");
        validRegisterRequest.setFirstname("John");
        validRegisterRequest.setLastname("Doe");

        validLoginRequest = new LoginRequest();
        validLoginRequest.setEmail("john.doe@example.com");
        validLoginRequest.setPassword("Secret@123");

        registeredUser = User.builder()
                .userId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                .email("john.doe@example.com")
                .firstname("John")
                .lastname("Doe")
                .role(User.Role.CLIENT)
                .build();

        loginResponse = LoginResponse.builder()
                .accessToken("access_token_value")
                .refreshToken("refresh_token_value")
                .email("john.doe@example.com")
                .firstname("John")
                .lastname("Doe")
                .role("CLIENT")
                .build();
    }

    // ── POST /api/v1/auth/register ─────────────────────────────────────────────

    @Test
    void register_shouldReturn201_whenRequestIsValid() throws Exception {
        // Arrange
        when(authService.register(any(RegisterRequest.class))).thenReturn(registeredUser);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Account created successfully"))
                .andExpect(jsonPath("$.userId").value("550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    void register_shouldReturn400_whenEmailIsInvalid() throws Exception {
        // Arrange
        validRegisterRequest.setEmail("not-an-email");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturn400_whenPasswordIsWeak() throws Exception {
        // Arrange — password without uppercase letter
        validRegisterRequest.setPassword("weakpassword1@");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturn400_whenFirstnameIsBlank() throws Exception {
        // Arrange
        validRegisterRequest.setFirstname("");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturn409_whenEmailAlreadyExists() throws Exception {
        // Arrange
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new BusinessException("Email already in use", HttpStatus.CONFLICT));

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isConflict());
    }

    // ── POST /api/v1/auth/login ────────────────────────────────────────────────

    @Test
    void login_shouldReturn200WithTokens_whenCredentialsAreValid() throws Exception {
        // Arrange
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

        // Act & Assert
        // The refresh token is set as an HttpOnly cookie and stripped from the JSON body.
        // The response must NOT include $.refreshToken.
        // We use header() matchers for Set-Cookie because MockMvc's cookie() matcher
        // only reads cookies set via HttpServletResponse.addCookie(), not raw Set-Cookie headers.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access_token_value"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(header().string(org.springframework.http.HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("refreshToken")))
                .andExpect(header().string(org.springframework.http.HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("HttpOnly")));
    }

    @Test
    void login_shouldReturn400_whenEmailIsBlank() throws Exception {
        // Arrange
        validLoginRequest.setEmail("");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_shouldReturn400_whenBodyIsMissing() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
