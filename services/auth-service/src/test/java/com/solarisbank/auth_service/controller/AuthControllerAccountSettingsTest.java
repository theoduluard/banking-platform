package com.solarisbank.auth_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.auth_service.config.SecurityConfig;
import com.solarisbank.auth_service.dto.ChangePasswordRequest;
import com.solarisbank.auth_service.dto.ConfirmEmailChangeOtpRequest;
import com.solarisbank.auth_service.dto.RequestEmailChangeRequest;
import com.solarisbank.auth_service.exception.BusinessException;
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

import java.security.Principal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for the authenticated account-settings endpoints of AuthController:
 * change-password, request-email-change, confirm-email-change-otp, verify-new-email.
 *
 * Principal is injected via MockMvc's .principal() — the security filter is excluded
 * so there is no Spring Security auth check, only the controller's own calls to
 * principal.getName().
 */
@WebMvcTest(
        value = AuthController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
        }
)
class AuthControllerAccountSettingsTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    /** Minimal Principal implementation that returns the user's email from getName(). */
    private static final Principal ALICE = () -> "alice@example.com";

    private ChangePasswordRequest validChangePwdRequest;
    private RequestEmailChangeRequest validEmailChangeRequest;
    private ConfirmEmailChangeOtpRequest validConfirmOtpRequest;

    @BeforeEach
    void setUp() {
        validChangePwdRequest = new ChangePasswordRequest();
        validChangePwdRequest.setCurrentPassword("OldPass@1");
        validChangePwdRequest.setNewPassword("NewPass@99");

        validEmailChangeRequest = new RequestEmailChangeRequest();
        validEmailChangeRequest.setNewEmail("newalice@example.com");
        validEmailChangeRequest.setCurrentPassword("OldPass@1");

        validConfirmOtpRequest = new ConfirmEmailChangeOtpRequest();
        validConfirmOtpRequest.setCode("123456");
    }

    // ── POST /api/v1/auth/change-password ──────────────────────────────────────

    @Test
    void changePassword_shouldReturn200_whenSuccessful() throws Exception {
        doNothing().when(authService).changePassword("alice@example.com", "OldPass@1", "NewPass@99");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChangePwdRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully. Please log in again."));

        verify(authService).changePassword("alice@example.com", "OldPass@1", "NewPass@99");
    }

    @Test
    void changePassword_shouldReturn400_whenCurrentPasswordBlank() throws Exception {
        validChangePwdRequest.setCurrentPassword("");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChangePwdRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void changePassword_shouldReturn400_whenNewPasswordTooShort() throws Exception {
        validChangePwdRequest.setNewPassword("short");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChangePwdRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void changePassword_shouldReturn401_whenCurrentPasswordIsWrong() throws Exception {
        doThrow(new BusinessException("Wrong current password", HttpStatus.UNAUTHORIZED))
                .when(authService).changePassword(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChangePwdRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_shouldReturn404_whenUserNotFound() throws Exception {
        doThrow(new BusinessException("User not found", HttpStatus.NOT_FOUND))
                .when(authService).changePassword(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChangePwdRequest)))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/v1/auth/request-email-change ────────────────────────────────

    @Test
    void requestEmailChange_shouldReturn200_whenSuccessful() throws Exception {
        doNothing().when(authService)
                .requestEmailChange("alice@example.com", "newalice@example.com", "OldPass@1");

        mockMvc.perform(post("/api/v1/auth/request-email-change")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEmailChangeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "A verification code has been sent to your current email address."));

        verify(authService).requestEmailChange("alice@example.com", "newalice@example.com", "OldPass@1");
    }

    @Test
    void requestEmailChange_shouldReturn400_whenNewEmailIsInvalid() throws Exception {
        validEmailChangeRequest.setNewEmail("not-an-email");

        mockMvc.perform(post("/api/v1/auth/request-email-change")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEmailChangeRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void requestEmailChange_shouldReturn400_whenCurrentPasswordBlank() throws Exception {
        validEmailChangeRequest.setCurrentPassword("");

        mockMvc.perform(post("/api/v1/auth/request-email-change")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEmailChangeRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void requestEmailChange_shouldReturn409_whenNewEmailAlreadyTaken() throws Exception {
        doThrow(new BusinessException("Email already in use", HttpStatus.CONFLICT))
                .when(authService).requestEmailChange(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/request-email-change")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEmailChangeRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    void requestEmailChange_shouldReturn401_whenPasswordIsWrong() throws Exception {
        doThrow(new BusinessException("Wrong current password", HttpStatus.UNAUTHORIZED))
                .when(authService).requestEmailChange(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/request-email-change")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEmailChangeRequest)))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/v1/auth/confirm-email-change-otp ────────────────────────────

    @Test
    void confirmEmailChangeOtp_shouldReturn200_whenCodeIsValid() throws Exception {
        doNothing().when(authService).confirmEmailChangeOtp("alice@example.com", "123456");

        mockMvc.perform(post("/api/v1/auth/confirm-email-change-otp")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validConfirmOtpRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Code verified. A confirmation link has been sent to your new email address."));

        verify(authService).confirmEmailChangeOtp("alice@example.com", "123456");
    }

    @Test
    void confirmEmailChangeOtp_shouldReturn400_whenCodeIsNotSixDigits() throws Exception {
        validConfirmOtpRequest.setCode("12345");  // only 5 digits

        mockMvc.perform(post("/api/v1/auth/confirm-email-change-otp")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validConfirmOtpRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void confirmEmailChangeOtp_shouldReturn400_whenCodeContainsLetters() throws Exception {
        validConfirmOtpRequest.setCode("abc123");

        mockMvc.perform(post("/api/v1/auth/confirm-email-change-otp")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validConfirmOtpRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void confirmEmailChangeOtp_shouldReturn401_whenCodeIsWrong() throws Exception {
        doThrow(new BusinessException("Invalid OTP code", HttpStatus.UNAUTHORIZED))
                .when(authService).confirmEmailChangeOtp(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/confirm-email-change-otp")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validConfirmOtpRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmEmailChangeOtp_shouldReturn404_whenNoPendingRequest() throws Exception {
        doThrow(new BusinessException("No pending email change request", HttpStatus.NOT_FOUND))
                .when(authService).confirmEmailChangeOtp(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/confirm-email-change-otp")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validConfirmOtpRequest)))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/auth/verify-new-email ─────────────────────────────────────

    @Test
    void verifyNewEmail_shouldReturn200_whenTokenIsValid() throws Exception {
        doNothing().when(authService).verifyNewEmail("valid-new-email-token");

        mockMvc.perform(get("/api/v1/auth/verify-new-email")
                        .param("token", "valid-new-email-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Email address updated successfully. Please log in with your new email."));

        verify(authService).verifyNewEmail("valid-new-email-token");
    }

    @Test
    void verifyNewEmail_shouldReturn404_whenTokenNotFound() throws Exception {
        doThrow(new BusinessException("Invalid or expired token", HttpStatus.NOT_FOUND))
                .when(authService).verifyNewEmail("bad-token");

        mockMvc.perform(get("/api/v1/auth/verify-new-email")
                        .param("token", "bad-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void verifyNewEmail_shouldReturn410_whenTokenExpired() throws Exception {
        doThrow(new BusinessException("Email change link has expired", HttpStatus.GONE))
                .when(authService).verifyNewEmail("expired-token");

        mockMvc.perform(get("/api/v1/auth/verify-new-email")
                        .param("token", "expired-token"))
                .andExpect(status().isGone());
    }

    @Test
    void verifyNewEmail_shouldReturn409_whenNewEmailAlreadyTaken() throws Exception {
        doThrow(new BusinessException("New email is already registered", HttpStatus.CONFLICT))
                .when(authService).verifyNewEmail(anyString());

        mockMvc.perform(get("/api/v1/auth/verify-new-email")
                        .param("token", "conflict-token"))
                .andExpect(status().isConflict());
    }
}
