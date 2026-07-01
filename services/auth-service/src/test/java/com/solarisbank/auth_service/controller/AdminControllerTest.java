package com.solarisbank.auth_service.controller;

import com.solarisbank.auth_service.config.SecurityConfig;
import com.solarisbank.auth_service.dto.UserAdminResponse;
import com.solarisbank.auth_service.exception.BusinessException;
import com.solarisbank.auth_service.model.User;
import com.solarisbank.auth_service.repository.UserRepository;
import com.solarisbank.auth_service.security.JwtAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = AdminController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
        }
)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    private UUID userId;
    private User activeClient;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        activeClient = User.builder()
                .userId(userId)
                .email("client@example.com")
                .firstname("Jean")
                .lastname("Dupont")
                .role(User.Role.CLIENT)
                .isActive(true)
                .createdAt(LocalDate.now())
                .build();
    }

    // ── GET /api/v1/admin/users ────────────────────────────────────────────────

    @Test
    void getAllUsers_shouldReturn200_withPagedUserList() throws Exception {
        Page<User> usersPage = new PageImpl<>(List.of(activeClient));
        when(userRepository.findAll(ArgumentMatchers.<Specification<User>>any(), ArgumentMatchers.<Pageable>any()))
                .thenReturn(usersPage);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.content[0].email").value("client@example.com"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAllUsers_shouldReturn403_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userRepository);
    }

    // ── PATCH /api/v1/admin/users/{id}/status ─────────────────────────────────

    @Test
    void updateUserStatus_shouldDeactivateClient() throws Exception {
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeClient));
        when(userRepository.save(any(User.class))).thenReturn(activeClient);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", userId)
                        .header("X-User-Role", "ADMIN")
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateUserStatus_shouldReturn404_whenUserNotFound() throws Exception {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", userId)
                        .header("X-User-Role", "ADMIN")
                        .param("active", "false"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateUserStatus_shouldReturn403_whenDeactivatingAdmin() throws Exception {
        activeClient.setRole(User.Role.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeClient));

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", userId)
                        .header("X-User-Role", "ADMIN")
                        .param("active", "false"))
                .andExpect(status().isForbidden());

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserStatus_shouldReturn403_whenCallerIsNotAdmin() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", userId)
                        .header("X-User-Role", "CLIENT")
                        .param("active", "false"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userRepository);
    }
}
