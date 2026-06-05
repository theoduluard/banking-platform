package com.solarisbank.messaging_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.messaging_service.dto.MessageResponse;
import com.solarisbank.messaging_service.exception.BusinessException;
import com.solarisbank.messaging_service.model.Message;
import com.solarisbank.messaging_service.service.MessagingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessageController.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessagingService messagingService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UUID userId;
    private UUID messageId;
    private MessageResponse messageResponse;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        messageId = UUID.randomUUID();

        messageResponse = MessageResponse.builder()
                .id(messageId)
                .userId(userId)
                .subject("Bienvenue")
                .body("Votre compte a été approuvé.")
                .type(Message.Type.APPROVAL)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── GET /api/v1/messages ──────────────────────────────────────────────────

    @Test
    void getMyMessages_shouldReturn200_withPage() throws Exception {
        Page<MessageResponse> page = new PageImpl<>(List.of(messageResponse));
        when(messagingService.getMyMessages(userId, 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/v1/messages")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(messageId.toString()))
                .andExpect(jsonPath("$.content[0].subject").value("Bienvenue"));
    }

    // ── GET /api/v1/messages/unread-count ─────────────────────────────────────

    @Test
    void getUnreadCount_shouldReturn200_withCount() throws Exception {
        when(messagingService.getUnreadCount(userId)).thenReturn(5L);

        mockMvc.perform(get("/api/v1/messages/unread-count")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    // ── PATCH /api/v1/messages/{id}/read ──────────────────────────────────────

    @Test
    void markAsRead_shouldReturn200_whenMessageFound() throws Exception {
        MessageResponse readMessage = MessageResponse.builder()
                .id(messageId).userId(userId)
                .subject("Bienvenue").body("Contenu.")
                .type(Message.Type.INFO).isRead(true)
                .createdAt(LocalDateTime.now()).build();

        when(messagingService.markAsRead(messageId, userId)).thenReturn(readMessage);

        mockMvc.perform(patch("/api/v1/messages/{id}/read", messageId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(messageId.toString()));
    }

    @Test
    void markAsRead_shouldReturn404_whenMessageNotFound() throws Exception {
        when(messagingService.markAsRead(messageId, userId))
                .thenThrow(new BusinessException("Message not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(patch("/api/v1/messages/{id}/read", messageId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void markAsRead_shouldReturn403_whenForbidden() throws Exception {
        when(messagingService.markAsRead(messageId, userId))
                .thenThrow(new BusinessException("Forbidden", HttpStatus.FORBIDDEN));

        mockMvc.perform(patch("/api/v1/messages/{id}/read", messageId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden());
    }
}
