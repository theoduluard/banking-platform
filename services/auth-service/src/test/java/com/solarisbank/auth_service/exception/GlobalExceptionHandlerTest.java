package com.solarisbank.auth_service.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ── BusinessException ──────────────────────────────────────────────────────

    @Test
    void handleBusinessException_401_shouldReturnUnauthorized() {
        BusinessException ex = new BusinessException("Invalid credentials", HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map<String, Object>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("status", 401);
        assertThat(response.getBody()).containsEntry("error", "Invalid credentials");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    void handleBusinessException_conflict_shouldReturn409() {
        BusinessException ex = new BusinessException("Email already in use", HttpStatus.CONFLICT);

        ResponseEntity<Map<String, Object>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("status", 409);
    }

    @Test
    void handleBusinessException_gone_shouldReturn410() {
        BusinessException ex = new BusinessException("OTP has expired", HttpStatus.GONE);

        ResponseEntity<Map<String, Object>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    // ── MethodArgumentNotValidException ────────────────────────────────────────

    @Test
    void handleValidationErrors_shouldReturn400_withFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult        = mock(BindingResult.class);
        FieldError fieldError = new FieldError("registerRequest", "email", "must be a valid email");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, Object>> response = handler.handleValidationErrors(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry("error", "Validation failed");
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertThat(fields).containsEntry("email", "must be a valid email");
    }

    @Test
    void handleValidationErrors_shouldHandleMultipleFields() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult        = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("obj", "password", "must be at least 8 characters"),
                new FieldError("obj", "firstname", "must not be blank")
        ));

        ResponseEntity<Map<String, Object>> response = handler.handleValidationErrors(ex);

        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertThat(fields).hasSize(2);
    }

    // ── HttpMessageNotReadableException ────────────────────────────────────────

    @Test
    void handleMissingBody_shouldReturn400() {
        ResponseEntity<Map<String, Object>> response = handler.handleMissingBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry("error", "Malformed or missing request body");
    }

    // ── AuthenticationException ────────────────────────────────────────────────

    @Test
    void handleAuthenticationException_badCredentials_shouldReturn401WithGenericMessage() {
        BadCredentialsException ex = new BadCredentialsException("bad");

        ResponseEntity<Map<String, Object>> response = handler.handleAuthenticationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Identifiants incorrects");
    }

    @Test
    void handleAuthenticationException_disabled_shouldReturn401WithDisabledMessage() {
        DisabledException ex = new DisabledException("disabled");

        ResponseEntity<Map<String, Object>> response = handler.handleAuthenticationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Compte désactivé");
    }

    // ── Generic exception ──────────────────────────────────────────────────────

    @Test
    void handleGenericException_shouldReturn500() {
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(new RuntimeException("test"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("status", 500);
        assertThat(response.getBody()).containsEntry("error", "An unexpected error occurred");
    }
}
