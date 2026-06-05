package com.solarisbank.api_gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtGatewayFilterTest {

    private static final String SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private JwtGatewayFilter filter;

    private String userId;
    private String email;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().toString();
        email  = "jean@solaris.com";
    }

    // ── Routes publiques ───────────────────────────────────────────────────────

    @Test
    void shouldPassThrough_forLoginPath() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull(); // filtre suivant appelé
    }

    @Test
    void shouldPassThrough_forRegisterPath() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void shouldPassThrough_forLogoutPath() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void shouldPassThrough_forVerifyEmailPath() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("GET", "/api/v1/auth/verify-email");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    // ── Header Authorization manquant ──────────────────────────────────────────

    @Test
    void shouldReturn401_whenAuthHeaderIsMissing() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("GET", "/api/v1/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull(); // filtre suivant NON appelé
    }

    @Test
    void shouldReturn401_whenAuthHeaderDoesNotStartWithBearer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    // ── Token invalide ─────────────────────────────────────────────────────────

    @Test
    void shouldReturn401_whenTokenIsInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Bearer invalid.token.here");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        when(jwtService.isTokenValid("invalid.token.here")).thenThrow(new RuntimeException("bad token"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void shouldReturn401_whenTokenIsExpired() throws Exception {
        String token = buildToken(email, userId, -1_000L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        when(jwtService.isTokenValid(token)).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    // ── Token valide ───────────────────────────────────────────────────────────

    @Test
    void shouldInjectXUserIdHeader_andPassThrough_whenTokenIsValid() throws Exception {
        String token = buildToken(email, userId, 3_600_000L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();

        // Vérifier que le header X-User-Id a été injecté dans la requête modifiée
        jakarta.servlet.http.HttpServletRequest wrappedRequest =
                (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        assertThat(wrappedRequest.getHeader("X-User-Id")).isEqualTo(userId);
    }

    // ── Admin path — blocked for non-admin ────────────────────────────────────

    @Test
    void shouldReturn403_whenAdminPath_andRoleIsNotAdmin() throws Exception {
        String token = buildToken(email, userId, "CLIENT", 3_600_000L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/accounts");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractRole(token)).thenReturn("CLIENT");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    // ── Admin path — allowed for admin role ───────────────────────────────────

    @Test
    void shouldPassThrough_forAdminPath_whenRoleIsAdmin() throws Exception {
        String token = buildToken(email, userId, "ADMIN", 3_600_000L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/accounts");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractRole(token)).thenReturn("ADMIN");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();

        HttpServletRequest wrapped = (HttpServletRequest) chain.getRequest();
        assertThat(wrapped.getHeader("X-User-Role")).isEqualTo("ADMIN");
    }

    // ── EnrichedRequestWrapper — getHeaders() and getHeaderNames() ─────────────

    @Test
    void shouldReturnEnumeration_fromGetHeaders_onEnrichedWrapper() throws Exception {
        String token = buildToken(email, userId, "CLIENT", 3_600_000L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractRole(token)).thenReturn("CLIENT");

        filter.doFilter(request, response, chain);

        HttpServletRequest wrapped = (HttpServletRequest) chain.getRequest();

        // getHeaders() for an injected header (hits the overridden branch)
        List<String> userIdValues = Collections.list(wrapped.getHeaders("X-User-Id"));
        assertThat(userIdValues).containsExactly(userId);

        List<String> roleValues = Collections.list(wrapped.getHeaders("X-User-Role"));
        assertThat(roleValues).containsExactly("CLIENT");

        // getHeader() / getHeaders() for a non-injected header → falls through to super
        assertThat(wrapped.getHeader("Authorization")).isEqualTo("Bearer " + token);
        assertThat(Collections.list(wrapped.getHeaders("Authorization")))
                .contains("Bearer " + token);
    }

    @Test
    void shouldIncludeInjectedNames_fromGetHeaderNames_onEnrichedWrapper() throws Exception {
        String token = buildToken(email, userId, "CLIENT", 3_600_000L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractRole(token)).thenReturn("CLIENT");

        filter.doFilter(request, response, chain);

        HttpServletRequest wrapped = (HttpServletRequest) chain.getRequest();

        List<String> names = Collections.list(wrapped.getHeaderNames());
        assertThat(names).contains("X-User-Id", "X-User-Role");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String buildToken(String subject, String userId, long expirationMs) {
        return buildToken(subject, userId, null, expirationMs);
    }

    private String buildToken(String subject, String userId, String role, long expirationMs) {
        var builder = Jwts.builder()
                .subject(subject)
                .claim("userId", userId)
                .expiration(new Date(System.currentTimeMillis() + expirationMs));
        if (role != null) {
            builder.claim("role", role);
        }
        return builder
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();
    }
}
