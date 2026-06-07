package com.solarisbank.notification_service.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handle_notFound_shouldReturn404() {
        BusinessException ex = new BusinessException("Notification not found", HttpStatus.NOT_FOUND);

        ResponseEntity<Map<String, Object>> response = handler.handle(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", 404);
        assertThat(response.getBody()).containsEntry("error", "Notification not found");
    }

    @Test
    void handle_badRequest_shouldReturn400() {
        BusinessException ex = new BusinessException("Invalid page size", HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> response = handler.handle(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
    }

    @Test
    void handle_unauthorized_shouldReturn401() {
        BusinessException ex = new BusinessException("Unauthorized", HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map<String, Object>> response = handler.handle(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
