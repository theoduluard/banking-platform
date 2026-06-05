package com.solarisbank.auth_service.security;

import com.solarisbank.auth_service.model.User;
import com.solarisbank.auth_service.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService     jwtService;
    @Mock private UserRepository userRepository;
    @InjectMocks private JwtAuthFilter jwtAuthFilter;

    @Mock private HttpServletRequest  request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain         filterChain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── No / invalid Authorization header ─────────────────────────────────────

    @Test
    void doFilter_shouldPassThrough_whenNoAuthorizationHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService, userRepository);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_shouldPassThrough_whenHeaderIsNotBearerToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService, userRepository);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ── Valid Bearer token ────────────────────────────────────────────────────

    @Test
    void doFilter_shouldSetAuthentication_whenTokenIsValid() throws Exception {
        User user = User.builder()
                .email("alice@example.com")
                .role(User.Role.CLIENT)
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer valid.jwt.token");
        when(jwtService.extractEmail("valid.jwt.token")).thenReturn("alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid("valid.jwt.token", "alice@example.com")).thenReturn(true);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("alice@example.com");
        assertThat(auth.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_CLIENT");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_shouldSetAdminRole_whenUserIsAdmin() throws Exception {
        User adminUser = User.builder()
                .email("admin@example.com")
                .role(User.Role.ADMIN)
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer admin.token");
        when(jwtService.extractEmail("admin.token")).thenReturn("admin@example.com");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(jwtService.isTokenValid("admin.token", "admin@example.com")).thenReturn(true);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    // ── Invalid / expired token ────────────────────────────────────────────────

    @Test
    void doFilter_shouldNotSetAuthentication_whenTokenIsInvalid() throws Exception {
        User user = User.builder()
                .email("alice@example.com")
                .role(User.Role.CLIENT)
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer expired.token");
        when(jwtService.extractEmail("expired.token")).thenReturn("alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid("expired.token", "alice@example.com")).thenReturn(false);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_shouldNotSetAuthentication_whenUserNotFound() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token.for.unknown");
        when(jwtService.extractEmail("token.for.unknown")).thenReturn("ghost@example.com");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_shouldNotSetAuthentication_whenEmailExtractedIsNull() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad.token");
        when(jwtService.extractEmail("bad.token")).thenReturn(null);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userRepository);
    }

    @Test
    void doFilter_shouldAlwaysCallFilterChain_afterProcessing() throws Exception {
        // Even when authentication succeeds, the filter chain must be called
        User user = User.builder()
                .email("alice@example.com")
                .role(User.Role.CLIENT)
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer valid.token");
        when(jwtService.extractEmail("valid.token")).thenReturn("alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid("valid.token", "alice@example.com")).thenReturn(true);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }
}
