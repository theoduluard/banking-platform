package com.solarisbank.notification_service.controller;

import com.solarisbank.notification_service.exception.BusinessException;
import com.solarisbank.notification_service.exception.GlobalExceptionHandler;
import com.solarisbank.notification_service.model.Notification;
import com.solarisbank.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Edge-case coverage for NotificationController:
 * - invalid (non-UUID) value in X-User-Id header → 400
 * - markRead when notification not found → 404
 */
@WebMvcTest(controllers = {NotificationController.class, GlobalExceptionHandler.class})
class NotificationControllerEdgeCasesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    private static final UUID USER_ID  = UUID.randomUUID();
    private static final UUID NOTIF_ID = UUID.randomUUID();

    // ── extractUserId — invalid UUID ───────────────────────────────────────────

    @Test
    void getNotifications_shouldReturn400_whenXUserIdIsNotAValidUuid() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Id", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUnreadCount_shouldReturn400_whenXUserIdIsNotAValidUuid() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("X-User-Id", "invalid-id"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void markRead_shouldReturn400_whenXUserIdIsNotAValidUuid() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", NOTIF_ID)
                        .header("X-User-Id", "bad-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void markAllRead_shouldReturn400_whenXUserIdIsNotAValidUuid() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .header("X-User-Id", "xyz"))
                .andExpect(status().isBadRequest());
    }

    // ── markRead — 404 ─────────────────────────────────────────────────────────

    @Test
    void markRead_shouldReturn404_whenNotificationDoesNotExist() throws Exception {
        when(notificationService.markRead(NOTIF_ID, USER_ID))
                .thenThrow(new BusinessException("Notification not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", NOTIF_ID)
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Notification not found"));
    }

    // ── markAllRead — zero updates ─────────────────────────────────────────────

    @Test
    void markAllRead_shouldReturn200_withZeroUpdated_whenAllAlreadyRead() throws Exception {
        when(notificationService.markAllRead(USER_ID)).thenReturn(0);

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(0));
    }

    // ── getNotifications — empty page ──────────────────────────────────────────

    @Test
    void getNotifications_shouldReturn200_withEmptyPage() throws Exception {
        when(notificationService.getNotifications(USER_ID, 0, 20))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        java.util.List.of()));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ── getUnreadCount — zero ──────────────────────────────────────────────────

    @Test
    void getUnreadCount_shouldReturn200_withZero_whenNoUnreadNotifications() throws Exception {
        when(notificationService.countUnread(USER_ID)).thenReturn(0L);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }
}
