package com.solarisbank.transaction_service.exception;

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
    void handleBusinessException_notFound_shouldReturn404() {
        BusinessException ex = new BusinessException("Transaction not found", HttpStatus.NOT_FOUND);

        ResponseEntity<Map<String, Object>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", 404);
        assertThat(response.getBody()).containsEntry("error", "Transaction not found");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    void handleBusinessException_badRequest_shouldReturn400() {
        BusinessException ex = new BusinessException("Same account", HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
    }

    @Test
    void handleBusinessException_forbidden_shouldReturn403() {
        BusinessException ex = new BusinessException("Access denied", HttpStatus.FORBIDDEN);

        ResponseEntity<Map<String, Object>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleBusinessException_methodNotAllowed_shouldReturn405() {
        BusinessException ex = new BusinessException("Insufficient funds", HttpStatus.METHOD_NOT_ALLOWED);

        ResponseEntity<Map<String, Object>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).containsEntry("error", "Insufficient funds");
    }

    // ── MethodArgumentNotValidException ────────────────────────────────────────

    @Test
    void handleValidationErrors_shouldReturn400_withFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult        = mock(BindingResult.class);
        FieldError fieldError = new FieldError("transferRequest", "amount", "must be positive");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, Object>> response = handler.handleValidationErrors(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Validation failed");
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertThat(fields).containsEntry("amount", "must be positive");
    }

    // ── Generic exception ──────────────────────────────────────────────────────

    @Test
    void handleGenericException_shouldReturn500() {
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(new RuntimeException("test"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("status", 500);
        assertThat(response.getBody()).containsEntry("error", "An unexpected error occurred");
        assertThat(response.getBody()).containsKey("timestamp");
    }
}
