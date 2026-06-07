package com.solarisbank.audit_service.controller;

import com.solarisbank.audit_service.model.AuditEvent;
import com.solarisbank.audit_service.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuditController.class)
class AuditControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuditEventRepository repo;

    private static final UUID USER_ID = UUID.randomUUID();

    private AuditEvent buildEvent(String type) {
        return AuditEvent.builder()
                .id(UUID.randomUUID())
                .eventType(type)
                .source("transaction-service")
                .userId(USER_ID)
                .entityType("TRANSACTION")
                .entityId(UUID.randomUUID())
                .payload("{}")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getMyEvents_shouldReturn200WithPage() throws Exception {
        when(repo.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(buildEvent("TRANSFER_COMPLETED"))));

        mockMvc.perform(get("/api/v1/audit/my-events")
                        .header("X-User-Id", USER_ID.toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("TRANSFER_COMPLETED"))
                .andExpect(jsonPath("$.content[0].source").value("transaction-service"));
    }

    @Test
    void getMyEvents_defaultPagination_shouldWork() throws Exception {
        when(repo.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/audit/my-events")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void getAllEvents_withoutEventType_shouldUseUnfilteredQuery() throws Exception {
        when(repo.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(buildEvent("LOGIN"))));

        mockMvc.perform(get("/api/v1/admin/audit/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("LOGIN"));

        verify(repo).findAllByOrderByCreatedAtDesc(any(Pageable.class));
    }

    @Test
    void getAllEvents_withEventType_shouldUseFilteredQuery() throws Exception {
        when(repo.findByEventTypeOrderByCreatedAtDesc(eq("TRANSACTION"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(buildEvent("TRANSACTION"))));

        mockMvc.perform(get("/api/v1/admin/audit/events")
                        .param("eventType", "TRANSACTION"))
                .andExpect(status().isOk());

        verify(repo).findByEventTypeOrderByCreatedAtDesc(eq("TRANSACTION"), any(Pageable.class));
    }
}
