package com.solarisbank.account_service.exception;

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
    void handleBusinessException_404_shouldReturnNotFound() {
        BusinessException ex = new BusinessException("Account not found", HttpStatus.NOT_FOUND);

        ResponseEntity<Map<String, Object>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", 404);
        assertThat(response.getBody()).containsEntry("error", "Account not found");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    void handleBusinessException_409_shouldReturnConflict() {
        BusinessException ex = new BusinessException("IBAN already exists", HttpStatus.CONFLICT);

        ResponseEntity<Map<String, Object>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("status", 409);
    }

    @Test
    void handleBusinessException_405_shouldReturnMethodNotAllowed() {
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
        FieldError fieldError = new FieldError("createAccountRequest", "type", "must not be null");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, Object>> response = handler.handleValidationErrors(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry("error", "Validation failed");
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertThat(fields).containsEntry("type", "must not be null");
    }

    @Test
    void handleValidationErrors_shouldCollectMultipleFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult        = mock(BindingResult.class);

        List<FieldError> errors = List.of(
                new FieldError("obj", "iban",   "must not be blank"),
                new FieldError("obj", "amount", "must be positive")
        );

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(errors);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationErrors(ex);

        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertThat(fields).hasSize(2).containsKey("iban").containsKey("amount");
    }

    // ── Generic exception ──────────────────────────────────────────────────────

    @Test
    void handleGenericException_shouldReturn500() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGenericException(new RuntimeException("unexpected"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("status", 500);
        assertThat(response.getBody()).containsEntry("error", "An unexpected error occurred");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    void handleGenericException_shouldReturn500_forNullPointerException() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGenericException(new NullPointerException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
