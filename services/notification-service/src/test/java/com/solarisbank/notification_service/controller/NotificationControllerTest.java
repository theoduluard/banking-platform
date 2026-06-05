package com.solarisbank.notification_service.controller;

import com.solarisbank.notification_service.exception.GlobalExceptionHandler;
import com.solarisbank.notification_service.model.Notification;
import com.solarisbank.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {NotificationController.class, GlobalExceptionHandler.class})
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID NOTIF_ID = UUID.randomUUID();

    private Notification buildNotification() {
        return Notification.builder()
                .id(NOTIF_ID)
                .userId(USER_ID)
                .type(Notification.Type.TRANSACTION_SENT)
                .title("Transfer sent")
                .message("Your transfer of 100 EUR has been completed.")
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── GET /api/v1/notifications ──────────────────────────────────────────────

    @Test
    void getNotifications_shouldReturn200_withNotifications() throws Exception {
        when(notificationService.getNotifications(eq(USER_ID), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(buildNotification())));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Id", USER_ID.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("TRANSACTION_SENT"))
                .andExpect(jsonPath("$.content[0].read").value(false));
    }

    @Test
    void getNotifications_shouldReturn401_whenNoUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNotifications_shouldCapPageSizeAt100() throws Exception {
        when(notificationService.getNotifications(eq(USER_ID), eq(0), eq(100)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/notifications?size=999")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk());

        verify(notificationService).getNotifications(USER_ID, 0, 100);
    }

    // ── GET /api/v1/notifications/unread-count ─────────────────────────────────

    @Test
    void getUnreadCount_shouldReturn200_withCount() throws Exception {
        when(notificationService.countUnread(USER_ID)).thenReturn(7L);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(7));
    }

    @Test
    void getUnreadCount_shouldReturn401_whenNoUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /api/v1/notifications/{id}/read ─────────────────────────────────

    @Test
    void markRead_shouldReturn200_withUpdatedNotification() throws Exception {
        Notification updated = buildNotification();
        updated.setRead(true);
        when(notificationService.markRead(NOTIF_ID, USER_ID)).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", NOTIF_ID)
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void markRead_shouldReturn401_whenNoUserIdHeader() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", NOTIF_ID))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /api/v1/notifications/read-all ──────────────────────────────────

    @Test
    void markAllRead_shouldReturn200_withUpdatedCount() throws Exception {
        when(notificationService.markAllRead(USER_ID)).thenReturn(4);

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(4));
    }

    @Test
    void markAllRead_shouldReturn401_whenNoUserIdHeader() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/read-all"))
                .andExpect(status().isUnauthorized());
    }
}
