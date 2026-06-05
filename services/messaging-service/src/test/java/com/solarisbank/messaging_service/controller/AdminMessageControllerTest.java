package com.solarisbank.messaging_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.messaging_service.dto.MessageResponse;
import com.solarisbank.messaging_service.model.Message;
import com.solarisbank.messaging_service.service.MessagingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminMessageController.class)
class AdminMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessagingService messagingService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UUID userId;
    private UUID messageId;
    private MessageResponse sampleMessage;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        messageId = UUID.randomUUID();

        sampleMessage = MessageResponse.builder()
                .id(messageId)
                .userId(userId)
                .subject("Bienvenue")
                .body("Votre compte a été approuvé.")
                .type(Message.Type.APPROVAL)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── POST /api/v1/admin/messages ───────────────────────────────────────────

    @Test
    void sendMessage_shouldReturn201_whenAdmin() throws Exception {
        when(messagingService.sendMessage(any())).thenReturn(sampleMessage);

        mockMvc.perform(post("/api/v1/admin/messages")
                        .header("X-User-Id",   userId.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId.toString(),
                                "subject", "Bienvenue",
                                "body", "Votre compte a été approuvé.",
                                "type", "APPROVAL"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(messageId.toString()))
                .andExpect(jsonPath("$.type").value("APPROVAL"));
    }

    @Test
    void sendMessage_shouldReturn403_whenNotAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/messages")
                        .header("X-User-Id",   userId.toString())
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId.toString(),
                                "subject", "Bienvenue",
                                "body", "Corps.",
                                "type", "INFO"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(messagingService);
    }

    @Test
    void sendMessage_shouldReturn400_whenBodyMissing() throws Exception {
        mockMvc.perform(post("/api/v1/admin/messages")
                        .header("X-User-Id",   userId.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId.toString(),
                                "subject", "Objet"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messagingService);
    }

    // ── GET /api/v1/admin/messages ────────────────────────────────────────────

    @Test
    void getAllMessages_shouldReturn200_whenAdmin() throws Exception {
        Page<MessageResponse> page = new PageImpl<>(List.of(sampleMessage));
        when(messagingService.getAllMessages(0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/messages")
                        .header("X-User-Id",   userId.toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(messageId.toString()));
    }

    @Test
    void getAllMessages_shouldReturn200_withPaginationParams() throws Exception {
        Page<MessageResponse> page = new PageImpl<>(List.of());
        when(messagingService.getAllMessages(2, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/messages")
                        .header("X-User-Id",   userId.toString())
                        .header("X-User-Role", "ADMIN")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(messagingService).getAllMessages(2, 10);
    }

    @Test
    void getAllMessages_shouldReturn403_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/messages")
                        .header("X-User-Id",   userId.toString())
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(messagingService);
    }
}
