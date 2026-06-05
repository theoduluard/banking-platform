package com.solarisbank.api_gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final String SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @InjectMocks
    private JwtService jwtService;

    private String email;
    private String userId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        email  = "jean@solaris.com";
        userId = UUID.randomUUID().toString();
    }

    // ── extractEmail ───────────────────────────────────────────────────────────

    @Test
    void extractEmail_shouldReturnSubject_fromValidToken() {
        String token = buildToken(email, userId, 3_600_000L);
        assertThat(jwtService.extractEmail(token)).isEqualTo(email);
    }

    // ── extractUserId ──────────────────────────────────────────────────────────

    @Test
    void extractUserId_shouldReturnUserId_fromValidToken() {
        String token = buildToken(email, userId, 3_600_000L);
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
    }

    // ── extractRole ───────────────────────────────────────────────────────────

    @Test
    void extractRole_shouldReturnRole_fromValidToken() {
        String token = Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", "CLIENT")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();

        assertThat(jwtService.extractRole(token)).isEqualTo("CLIENT");
    }

    // ── isTokenValid ───────────────────────────────────────────────────────────

    @Test
    void isTokenValid_shouldReturnTrue_whenTokenIsValid() {
        String token = buildToken(email, userId, 3_600_000L);
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_shouldReturnFalseOrThrow_whenTokenIsExpired() {
        // Générer un token déjà expiré (expiration = -1s)
        String token = buildToken(email, userId, -1_000L);

        boolean valid;
        try {
            valid = jwtService.isTokenValid(token);
        } catch (Exception e) {
            // ExpiredJwtException — comportement attendu
            return;
        }
        assertThat(valid).isFalse();
    }

    @Test
    void isTokenValid_shouldThrow_whenTokenIsSignedWithDifferentKey() {
        // Token signé avec une clé différente
        String differentSecret =
                "5E537368566B5970404E635266556A586E3272357538782F413F4428472B4B62";
        String token = Jwts.builder()
                .subject(email)
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(differentSecret)))
                .compact();

        assertThatThrownBy(() -> jwtService.isTokenValid(token));
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
