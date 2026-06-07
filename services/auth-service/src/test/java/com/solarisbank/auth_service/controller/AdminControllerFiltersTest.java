package com.solarisbank.auth_service.controller;

import com.solarisbank.auth_service.config.SecurityConfig;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Additional tests for AdminController focusing on:
 * - getAllUsers filtering by search, role, status
 * - getAllUsers sorting by different fields
 * - MAX_PAGE_SIZE cap (>200 → clamped to 200)
 * - updateUserStatus re-activation (active=true)
 */
@WebMvcTest(
        value = AdminController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
        }
)
class AdminControllerFiltersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    private UUID userId;
    private User clientUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        clientUser = User.builder()
                .userId(userId)
                .email("client@example.com")
                .firstname("Marie")
                .lastname("Dupont")
                .role(User.Role.CLIENT)
                .isActive(false)     // starts inactive so we can test activation
                .createdAt(LocalDate.now())
                .build();
    }

    // ── getAllUsers — search filter ────────────────────────────────────────────

    @Test
    void getAllUsers_withSearch_shouldPassSearchToRepository() throws Exception {
        Page<User> emptyPage = new PageImpl<>(List.of());
        when(userRepository.findWithFilters(eq("dupont"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("search", "dupont"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(userRepository).findWithFilters(eq("dupont"), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void getAllUsers_withBlankSearch_shouldTreatAsNoFilter() throws Exception {
        Page<User> emptyPage = new PageImpl<>(List.of());
        when(userRepository.findWithFilters(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("search", "   "))  // blank → treated as null
                .andExpect(status().isOk());

        verify(userRepository).findWithFilters(isNull(), isNull(), isNull(), any(Pageable.class));
    }

    // ── getAllUsers — role filter ──────────────────────────────────────────────

    @Test
    void getAllUsers_withRoleClient_shouldPassClientRoleEnum() throws Exception {
        Page<User> page = new PageImpl<>(List.of(clientUser));
        when(userRepository.findWithFilters(isNull(), eq(User.Role.CLIENT), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("client@example.com"));

        verify(userRepository).findWithFilters(isNull(), eq(User.Role.CLIENT), isNull(), any(Pageable.class));
    }

    @Test
    void getAllUsers_withRoleAll_shouldPassNullRoleToRepository() throws Exception {
        Page<User> emptyPage = new PageImpl<>(List.of());
        when(userRepository.findWithFilters(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage);

        // role=ALL should be treated as "no filter" → null passed to repository
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("role", "ALL"))
                .andExpect(status().isOk());

        verify(userRepository).findWithFilters(isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void getAllUsers_withRoleAdmin_shouldPassAdminRoleEnum() throws Exception {
        Page<User> emptyPage = new PageImpl<>(List.of());
        when(userRepository.findWithFilters(isNull(), eq(User.Role.ADMIN), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("role", "ADMIN"))
                .andExpect(status().isOk());

        verify(userRepository).findWithFilters(isNull(), eq(User.Role.ADMIN), isNull(), any(Pageable.class));
    }

    // ── getAllUsers — status filter ────────────────────────────────────────────

    @Test
    void getAllUsers_withStatusActive_shouldPassIsTrueToRepository() throws Exception {
        Page<User> emptyPage = new PageImpl<>(List.of());
        when(userRepository.findWithFilters(isNull(), isNull(), eq(Boolean.TRUE), any(Pageable.class)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk());

        verify(userRepository).findWithFilters(isNull(), isNull(), eq(Boolean.TRUE), any(Pageable.class));
    }

    @Test
    void getAllUsers_withStatusInactive_shouldPassIsFalseToRepository() throws Exception {
        Page<User> page = new PageImpl<>(List.of(clientUser));
        when(userRepository.findWithFilters(isNull(), isNull(), eq(Boolean.FALSE), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(userId.toString()));

        verify(userRepository).findWithFilters(isNull(), isNull(), eq(Boolean.FALSE), any(Pageable.class));
    }

    @Test
    void getAllUsers_withUnknownStatus_shouldPassNullToRepository() throws Exception {
        Page<User> emptyPage = new PageImpl<>(List.of());
        when(userRepository.findWithFilters(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("status", "PENDING"))  // unknown → null
                .andExpect(status().isOk());

        verify(userRepository).findWithFilters(isNull(), isNull(), isNull(), any(Pageable.class));
    }

    // ── getAllUsers — sort options ─────────────────────────────────────────────

    @Test
    void getAllUsers_sortByEmail_shouldUseSortFieldEmail() throws Exception {
        Page<User> emptyPage = new PageImpl<>(List.of());
        when(userRepository.findWithFilters(any(), any(), any(), argThat(p ->
                p.getSort().getOrderFor("email") != null)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("sortBy", "email")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk());

        verify(userRepository).findWithFilters(any(), any(), any(), argThat(p ->
                p.getSort().getOrderFor("email") != null));
    }

    @Test
    void getAllUsers_sortByDate_shouldUseSortFieldCreatedAt() throws Exception {
        Page<User> emptyPage = new PageImpl<>(List.of());
        when(userRepository.findWithFilters(any(), any(), any(), argThat(p ->
                p.getSort().getOrderFor("createdAt") != null)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("sortBy", "date")
                        .param("sortDir", "desc"))
                .andExpect(status().isOk());

        verify(userRepository).findWithFilters(any(), any(), any(), argThat(p ->
                p.getSort().getOrderFor("createdAt") != null));
    }

    @Test
    void getAllUsers_sortByUnknown_shouldDefaultToLastname() throws Exception {
        Page<User> emptyPage = new PageImpl<>(List.of());
        when(userRepository.findWithFilters(any(), any(), any(), argThat(p ->
                p.getSort().getOrderFor("lastname") != null)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("sortBy", "unknown"))
                .andExpect(status().isOk());

        verify(userRepository).findWithFilters(any(), any(), any(), argThat(p ->
                p.getSort().getOrderFor("lastname") != null));
    }

    // ── getAllUsers — MAX_PAGE_SIZE cap ───────────────────────────────────────

    @Test
    void getAllUsers_sizeExceedsMax_shouldCapAt200() throws Exception {
        Page<User> emptyPage = new PageImpl<>(List.of());
        when(userRepository.findWithFilters(any(), any(), any(), argThat(p ->
                p.getPageSize() == 200)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("size", "9999"))  // exceeds MAX_PAGE_SIZE=200
                .andExpect(status().isOk());

        verify(userRepository).findWithFilters(any(), any(), any(), argThat(p ->
                p.getPageSize() == 200));
    }

    // ── updateUserStatus — re-activation ──────────────────────────────────────

    @Test
    void updateUserStatus_shouldReactivateInactiveClient() throws Exception {
        // clientUser starts inactive
        when(userRepository.findById(userId)).thenReturn(Optional.of(clientUser));
        when(userRepository.save(any(User.class))).thenReturn(clientUser);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", userId)
                        .header("X-User-Role", "ADMIN")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));

        verify(userRepository).save(argThat(u -> Boolean.TRUE.equals(u.getIsActive())));
    }

    @Test
    void updateUserStatus_shouldAllowActivatingAdmin() throws Exception {
        // An ADMIN user can be activated (only deactivation is forbidden)
        User adminUser = User.builder()
                .userId(userId)
                .email("admin@example.com")
                .firstname("Super")
                .lastname("Admin")
                .role(User.Role.ADMIN)
                .isActive(false)
                .createdAt(LocalDate.now())
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(adminUser));
        when(userRepository.save(any(User.class))).thenReturn(adminUser);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", userId)
                        .header("X-User-Role", "ADMIN")
                        .param("active", "true"))
                .andExpect(status().isOk());

        verify(userRepository).save(any(User.class));
    }

    // ── getAllUsers — combined filters ────────────────────────────────────────

    @Test
    void getAllUsers_allFilters_shouldPassAllToRepository() throws Exception {
        Page<User> page = new PageImpl<>(List.of(clientUser));
        when(userRepository.findWithFilters(
                eq("marie"),
                eq(User.Role.CLIENT),
                eq(Boolean.FALSE),
                any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("search", "marie")
                        .param("role", "CLIENT")
                        .param("status", "INACTIVE")
                        .param("sortBy", "email")
                        .param("sortDir", "desc")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("client@example.com"));
    }
}
