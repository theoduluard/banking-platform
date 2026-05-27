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

import java.util.Date;
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

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String buildToken(String subject, String userId, long expirationMs) {
        return Jwts.builder()
                .subject(subject)
                .claim("userId", userId)
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();
    }
}
