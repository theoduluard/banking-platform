package com.solarisbank.messaging_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.messaging_service.dto.SupportRequestDetailResponse;
import com.solarisbank.messaging_service.dto.SupportRequestResponse;
import com.solarisbank.messaging_service.exception.BusinessException;
import com.solarisbank.messaging_service.model.SupportRequest;
import com.solarisbank.messaging_service.model.SupportRequestReply;
import com.solarisbank.messaging_service.service.MessagingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
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

@WebMvcTest(RequestController.class)
class RequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessagingService messagingService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UUID userId;
    private UUID requestId;
    private SupportRequestResponse sampleResponse;
    private SupportRequestDetailResponse detailResponse;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        requestId = UUID.randomUUID();

        sampleResponse = SupportRequestResponse.builder()
                .id(requestId)
                .userId(userId)
                .type(SupportRequest.Type.OTHER)
                .subject("Problème de connexion")
                .body("Je n'arrive pas à me connecter.")
                .status(SupportRequest.Status.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        detailResponse = SupportRequestDetailResponse.builder()
                .id(requestId)
                .userId(userId)
                .type(SupportRequest.Type.OTHER)
                .subject("Problème de connexion")
                .body("Je n'arrive pas à me connecter.")
                .status(SupportRequest.Status.OPEN)
                .replies(List.of())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── POST /api/v1/requests ─────────────────────────────────────────────────

    @Test
    void createRequest_shouldReturn201_withResponse() throws Exception {
        when(messagingService.createRequest(eq(userId), any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/requests")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "OTHER",
                                "subject", "Problème de connexion",
                                "body", "Je n'arrive pas à me connecter."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void createRequest_shouldReturn400_whenSubjectMissing() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "OTHER",
                                "body", "Corps."))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messagingService);
    }

    @Test
    void createRequest_shouldReturn400_whenTypeMissing() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", "Objet",
                                "body", "Corps."))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messagingService);
    }

    // ── GET /api/v1/requests ──────────────────────────────────────────────────

    @Test
    void getMyRequests_shouldReturn200_withList() throws Exception {
        when(messagingService.getMyRequests(userId)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/requests")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId.toString()))
                .andExpect(jsonPath("$[0].type").value("OTHER"));
    }

    @Test
    void getMyRequests_shouldReturn200_withEmptyList() throws Exception {
        when(messagingService.getMyRequests(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/requests")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── GET /api/v1/requests/{id} ─────────────────────────────────────────────

    @Test
    void getRequestDetail_shouldReturn200_whenFound() throws Exception {
        when(messagingService.getRequestDetail(requestId, userId)).thenReturn(detailResponse);

        mockMvc.perform(get("/api/v1/requests/{id}", requestId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.replies").isArray());
    }

    @Test
    void getRequestDetail_shouldReturn404_whenNotFound() throws Exception {
        when(messagingService.getRequestDetail(requestId, userId))
                .thenThrow(new BusinessException("Request not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/v1/requests/{id}", requestId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRequestDetail_shouldReturn403_whenForbidden() throws Exception {
        when(messagingService.getRequestDetail(requestId, userId))
                .thenThrow(new BusinessException("Forbidden", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/api/v1/requests/{id}", requestId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/v1/requests/{id}/replies ────────────────────────────────────

    @Test
    void addReply_shouldReturn201_withDetailResponse() throws Exception {
        SupportRequestDetailResponse withReply = SupportRequestDetailResponse.builder()
                .id(requestId).userId(userId)
                .type(SupportRequest.Type.OTHER)
                .subject("Problème de connexion")
                .body("Corps.")
                .status(SupportRequest.Status.OPEN)
                .replies(List.of(SupportRequestDetailResponse.ReplyResponse.builder()
                        .id(UUID.randomUUID())
                        .authorType(SupportRequestReply.AuthorType.CLIENT)
                        .authorId(userId)
                        .body("Ma réponse.")
                        .createdAt(LocalDateTime.now())
                        .build()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(messagingService.addClientReply(eq(requestId), eq(userId), any()))
                .thenReturn(withReply);

        mockMvc.perform(post("/api/v1/requests/{id}/replies", requestId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Ma réponse."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replies[0].body").value("Ma réponse."));
    }

    @Test
    void addReply_shouldReturn400_whenBodyIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/requests/{id}/replies", requestId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", ""))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messagingService);
    }

    @Test
    void addReply_shouldReturn400_whenRequestIsClosed() throws Exception {
        when(messagingService.addClientReply(eq(requestId), eq(userId), any()))
                .thenThrow(new BusinessException("Cannot reply to a closed request", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v1/requests/{id}/replies", requestId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Corps."))))
                .andExpect(status().isBadRequest());
    }

    // ── InternalRequestFilter — invalid UUID path ─────────────────────────────

    @Test
    void createRequest_shouldReturn401_whenXUserIdIsNotAValidUUID() throws Exception {
        // Non-blank, non-UUID X-User-Id with no internal secret triggers 401
        mockMvc.perform(post("/api/v1/requests")
                        .header("X-User-Id", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "OTHER",
                                "subject", "Problème",
                                "body", "Corps."))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(messagingService);
    }

    // ── GlobalExceptionHandler — 500 path ─────────────────────────────────────

    @Test
    void createRequest_shouldReturn500_whenUnexpectedErrorOccurs() throws Exception {
        when(messagingService.createRequest(eq(userId), any()))
                .thenThrow(new RuntimeException("Unexpected server failure"));

        mockMvc.perform(post("/api/v1/requests")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "OTHER",
                                "subject", "Problème",
                                "body", "Corps."))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }
}
