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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminRequestController.class)
class AdminRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessagingService messagingService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UUID adminId;
    private UUID requestId;
    private SupportRequestResponse sampleResponse;
    private SupportRequestDetailResponse detailResponse;

    @BeforeEach
    void setUp() {
        adminId   = UUID.randomUUID();
        requestId = UUID.randomUUID();

        sampleResponse = SupportRequestResponse.builder()
                .id(requestId)
                .userId(UUID.randomUUID())
                .type(SupportRequest.Type.OTHER)
                .subject("Problème")
                .body("Corps.")
                .status(SupportRequest.Status.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        detailResponse = SupportRequestDetailResponse.builder()
                .id(requestId)
                .userId(UUID.randomUUID())
                .type(SupportRequest.Type.OTHER)
                .subject("Problème")
                .body("Corps.")
                .status(SupportRequest.Status.OPEN)
                .replies(List.of())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── GET /api/v1/admin/requests ────────────────────────────────────────────

    @Test
    void getAllRequests_shouldReturn200_whenAdmin() throws Exception {
        Page<SupportRequestResponse> page = new PageImpl<>(List.of(sampleResponse));
        when(messagingService.getAllRequests(0, 20, null)).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/requests")
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(requestId.toString()));
    }

    @Test
    void getAllRequests_shouldReturn200_withStatusFilter() throws Exception {
        Page<SupportRequestResponse> page = new PageImpl<>(List.of(sampleResponse));
        when(messagingService.getAllRequests(0, 20, SupportRequest.Status.OPEN)).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/requests")
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "ADMIN")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));

        verify(messagingService).getAllRequests(0, 20, SupportRequest.Status.OPEN);
    }

    @Test
    void getAllRequests_shouldReturn403_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/requests")
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(messagingService);
    }

    // ── GET /api/v1/admin/requests/stats ──────────────────────────────────────

    @Test
    void getStats_shouldReturn200_withOpenCount() throws Exception {
        when(messagingService.countOpenRequests()).thenReturn(5L);

        mockMvc.perform(get("/api/v1/admin/requests/stats")
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openCount").value(5));
    }

    @Test
    void getStats_shouldReturn403_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/requests/stats")
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(messagingService);
    }

    // ── GET /api/v1/admin/requests/{id} ───────────────────────────────────────

    @Test
    void getRequestDetail_shouldReturn200_whenAdmin() throws Exception {
        when(messagingService.getRequestDetailAdmin(requestId)).thenReturn(detailResponse);

        mockMvc.perform(get("/api/v1/admin/requests/{id}", requestId)
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()));
    }

    @Test
    void getRequestDetail_shouldReturn404_whenNotFound() throws Exception {
        when(messagingService.getRequestDetailAdmin(requestId))
                .thenThrow(new BusinessException("Request not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/v1/admin/requests/{id}", requestId)
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRequestDetail_shouldReturn403_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/requests/{id}", requestId)
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(messagingService);
    }

    // ── POST /api/v1/admin/requests/{id}/replies ──────────────────────────────

    @Test
    void adminReply_shouldReturn201_whenAdmin() throws Exception {
        SupportRequestDetailResponse withReply = SupportRequestDetailResponse.builder()
                .id(requestId).userId(UUID.randomUUID())
                .type(SupportRequest.Type.OTHER)
                .subject("Problème").body("Corps.")
                .status(SupportRequest.Status.IN_PROGRESS)
                .replies(List.of(SupportRequestDetailResponse.ReplyResponse.builder()
                        .id(UUID.randomUUID())
                        .authorType(SupportRequestReply.AuthorType.ADMIN)
                        .authorId(adminId)
                        .body("Nous traitons votre demande.")
                        .createdAt(LocalDateTime.now())
                        .build()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(messagingService.adminReply(eq(requestId), eq(adminId), any()))
                .thenReturn(withReply);

        mockMvc.perform(post("/api/v1/admin/requests/{id}/replies", requestId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("body", "Nous traitons votre demande."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.replies[0].body").value("Nous traitons votre demande."));
    }

    @Test
    void adminReply_shouldReturn201_withExplicitNewStatus() throws Exception {
        SupportRequestDetailResponse resolved = SupportRequestDetailResponse.builder()
                .id(requestId).userId(UUID.randomUUID())
                .type(SupportRequest.Type.OTHER)
                .subject("Problème").body("Corps.")
                .status(SupportRequest.Status.RESOLVED)
                .replies(List.of())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(messagingService.adminReply(eq(requestId), eq(adminId), any()))
                .thenReturn(resolved);

        mockMvc.perform(post("/api/v1/admin/requests/{id}/replies", requestId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "Résolu.",
                                "newStatus", "RESOLVED"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void adminReply_shouldReturn403_whenNotAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/requests/{id}/replies", requestId)
                        .header("X-User-Role", "CLIENT")
                        .header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Corps."))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(messagingService);
    }

    @Test
    void adminReply_shouldReturn400_whenBodyIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/admin/requests/{id}/replies", requestId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", ""))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messagingService);
    }
}
