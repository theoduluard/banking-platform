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
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

/**
 * Unit tests for AdminController — getAllUsers filtering, sorting, pagination cap,
 * and updateUserStatus activation flows.
 *
 * <p>The controller now uses JpaSpecificationExecutor.findAll(Specification, Pageable)
 * instead of a custom @Query method. Filter logic is encapsulated in the Specification
 * lambda, so these tests verify HTTP behaviour (status codes, response shape, pageable
 * sort/size) rather than repository argument details.
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
                .isActive(false)
                .createdAt(LocalDate.now())
                .build();

        // Default stub: any Specification + any Pageable → empty page
        when(userRepository.findAll(ArgumentMatchers.<Specification<User>>any(), ArgumentMatchers.<Pageable>any()))
                .thenReturn(new PageImpl<>(List.of()));
    }

    // ── getAllUsers — search filter ────────────────────────────────────────────

    @Test
    void getAllUsers_withSearch_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("search", "dupont"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(userRepository).findAll(ArgumentMatchers.<Specification<User>>any(), ArgumentMatchers.<Pageable>any());
    }

    @Test
    void getAllUsers_withBlankSearch_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("search", "   "))
                .andExpect(status().isOk());

        verify(userRepository).findAll(ArgumentMatchers.<Specification<User>>any(), ArgumentMatchers.<Pageable>any());
    }

    // ── getAllUsers — role filter ──────────────────────────────────────────────

    @Test
    void getAllUsers_withRoleClient_shouldReturnMatchingUsers() throws Exception {
        when(userRepository.findAll(ArgumentMatchers.<Specification<User>>any(), ArgumentMatchers.<Pageable>any()))
                .thenReturn(new PageImpl<>(List.of(clientUser)));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("client@example.com"));
    }

    @Test
    void getAllUsers_withRoleAll_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("role", "ALL"))
                .andExpect(status().isOk());
    }

    @Test
    void getAllUsers_withRoleAdmin_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("role", "ADMIN"))
                .andExpect(status().isOk());
    }

    // ── getAllUsers — status filter ────────────────────────────────────────────

    @Test
    void getAllUsers_withStatusActive_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk());
    }

    @Test
    void getAllUsers_withStatusInactive_shouldReturnInactiveUsers() throws Exception {
        when(userRepository.findAll(ArgumentMatchers.<Specification<User>>any(), ArgumentMatchers.<Pageable>any()))
                .thenReturn(new PageImpl<>(List.of(clientUser)));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(userId.toString()));
    }

    @Test
    void getAllUsers_withUnknownStatus_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("status", "PENDING"))
                .andExpect(status().isOk());
    }

    // ── getAllUsers — sort options ─────────────────────────────────────────────

    @Test
    void getAllUsers_sortByEmail_shouldUseEmailSort() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("sortBy", "email")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk());

        verify(userRepository).findAll(ArgumentMatchers.<Specification<User>>any(), ArgumentMatchers.<Pageable>argThat((Pageable p) ->
                p.getSort().getOrderFor("email") != null));
    }

    @Test
    void getAllUsers_sortByDate_shouldUseCreatedAtSort() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("sortBy", "date")
                        .param("sortDir", "desc"))
                .andExpect(status().isOk());

        verify(userRepository).findAll(ArgumentMatchers.<Specification<User>>any(), ArgumentMatchers.<Pageable>argThat((Pageable p) ->
                p.getSort().getOrderFor("createdAt") != null));
    }

    @Test
    void getAllUsers_sortByUnknown_shouldDefaultToLastname() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("sortBy", "unknown"))
                .andExpect(status().isOk());

        verify(userRepository).findAll(ArgumentMatchers.<Specification<User>>any(), ArgumentMatchers.<Pageable>argThat((Pageable p) ->
                p.getSort().getOrderFor("lastname") != null));
    }

    // ── getAllUsers — MAX_PAGE_SIZE cap ────────────────────────────────────────

    @Test
    void getAllUsers_sizeExceedsMax_shouldCapAt200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Role", "ADMIN")
                        .param("size", "9999"))
                .andExpect(status().isOk());

        verify(userRepository).findAll(ArgumentMatchers.<Specification<User>>any(), ArgumentMatchers.<Pageable>argThat((Pageable p) ->
                p.getPageSize() == 200));
    }

    // ── updateUserStatus — re-activation ──────────────────────────────────────

    @Test
    void updateUserStatus_shouldReactivateInactiveClient() throws Exception {
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
    void getAllUsers_allFilters_shouldReturnMatchingUsers() throws Exception {
        when(userRepository.findAll(ArgumentMatchers.<Specification<User>>any(), ArgumentMatchers.<Pageable>any()))
                .thenReturn(new PageImpl<>(List.of(clientUser)));

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
