package com.solarisbank.messaging_service.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    void handleBusiness_notFound_shouldReturn404() {
        BusinessException ex = new BusinessException("Message not found", HttpStatus.NOT_FOUND);

        ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", 404);
        assertThat(response.getBody()).containsEntry("error", "Message not found");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    void handleBusiness_forbidden_shouldReturn403() {
        BusinessException ex = new BusinessException("Forbidden", HttpStatus.FORBIDDEN);

        ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleBusiness_badRequest_shouldReturn400() {
        BusinessException ex = new BusinessException("Cannot reply to closed request", HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Cannot reply to closed request");
    }

    // ── MethodArgumentNotValidException ────────────────────────────────────────

    @Test
    void handleValidationErrors_shouldReturn400_withFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult        = mock(BindingResult.class);
        FieldError fieldError = new FieldError("sendMessageRequest", "subject", "must not be blank");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, Object>> response = handler.handleValidationErrors(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Validation failed");
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertThat(fields).containsEntry("subject", "must not be blank");
    }

    @Test
    void handleValidationErrors_shouldHandleEmptyFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult        = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = handler.handleValidationErrors(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertThat(fields).isEmpty();
    }

    // ── Generic exception ──────────────────────────────────────────────────────

    @Test
    void handleGeneric_shouldReturn500() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneric(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("status", 500);
        assertThat(response.getBody()).containsEntry("error", "An unexpected error occurred");
    }
}
