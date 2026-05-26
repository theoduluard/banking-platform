package com.solarisbank.auth_service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    // Same key as application.properties (must be a valid Base64-encoded 256-bit key)
    private static final String SECRET_KEY = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION = 86_400_000L;       // 24 h
    private static final long REFRESH_EXPIRATION = 604_800_000L; // 7 days

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(jwtService, "expiration", EXPIRATION);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", REFRESH_EXPIRATION);
    }

    // ── generateAccessToken ────────────────────────────────────────────────────

    @Test
    void generateAccessToken_shouldReturnNonNullToken() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act
        String token = jwtService.generateAccessToken("user@example.com", "CLIENT", userId);

        // Assert
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    void generateAccessToken_shouldEmbedEmailAsSubject() {
        // Arrange
        String email = "user@example.com";
        UUID userId = UUID.randomUUID();

        // Act
        String token = jwtService.generateAccessToken(email, "CLIENT", userId);

        // Assert
        assertThat(jwtService.extractEmail(token)).isEqualTo(email);
    }

    @Test
    void generateAccessToken_tokenShouldBeValid() {
        // Arrange
        String email = "user@example.com";
        UUID userId = UUID.randomUUID();

        // Act
        String token = jwtService.generateAccessToken(email, "CLIENT", userId);

        // Assert
        assertThat(jwtService.isTokenValid(token, email)).isTrue();
    }

    // ── generateRefreshToken ───────────────────────────────────────────────────

    @Test
    void generateRefreshToken_shouldReturnNonNullToken() {
        // Act
        String token = jwtService.generateRefreshToken("user@example.com");

        // Assert
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    void generateRefreshToken_shouldEmbedEmailAsSubject() {
        // Arrange
        String email = "refresh@example.com";

        // Act
        String token = jwtService.generateRefreshToken(email);

        // Assert
        assertThat(jwtService.extractEmail(token)).isEqualTo(email);
    }

    // ── isTokenValid ───────────────────────────────────────────────────────────

    @Test
    void isTokenValid_shouldReturnFalse_whenEmailDoesNotMatch() {
        // Arrange
        String token = jwtService.generateAccessToken("alice@example.com", "CLIENT", UUID.randomUUID());

        // Act & Assert
        assertThat(jwtService.isTokenValid(token, "bob@example.com")).isFalse();
    }

    @Test
    void isTokenValid_shouldThrowOrReturnFalse_whenTokenIsExpired() {
        // Arrange — generate token with -1 ms so it is already expired at creation time
        ReflectionTestUtils.setField(jwtService, "expiration", -1L);
        String token = jwtService.generateAccessToken("user@example.com", "CLIENT", UUID.randomUUID());
        // Restore normal expiration
        ReflectionTestUtils.setField(jwtService, "expiration", EXPIRATION);

        // JJWT throws ExpiredJwtException when parsing an expired token rather than
        // returning false, so we verify that isTokenValid either returns false
        // OR throws an exception — both signal an invalid/expired token.
        boolean valid;
        try {
            valid = jwtService.isTokenValid(token, "user@example.com");
        } catch (Exception e) {
            // ExpiredJwtException → expected behaviour, token is indeed not valid
            return;
        }
        assertThat(valid).isFalse();
    }

    // ── extractEmail ───────────────────────────────────────────────────────────

    @Test
    void extractEmail_shouldReturnCorrectEmail() {
        // Arrange
        String email = "extract@example.com";
        String token = jwtService.generateAccessToken(email, "CLIENT", UUID.randomUUID());

        // Act & Assert
        assertThat(jwtService.extractEmail(token)).isEqualTo(email);
    }

    @Test
    void extractEmail_shouldThrow_whenTokenIsTamperedWith() {
        // Arrange
        String token = jwtService.generateAccessToken("user@example.com", "CLIENT", UUID.randomUUID());
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        // Act & Assert
        assertThatThrownBy(() -> jwtService.extractEmail(tampered))
                .isInstanceOf(Exception.class);
    }

    // ── access vs refresh tokens are different ────────────────────────────────

    @Test
    void accessAndRefreshTokens_shouldBeDifferent_forSameEmail() {
        // Arrange
        String email = "same@example.com";
        UUID userId = UUID.randomUUID();

        // Act
        String accessToken = jwtService.generateAccessToken(email, "CLIENT", userId);
        String refreshToken = jwtService.generateRefreshToken(email);

        // Assert
        assertThat(accessToken).isNotEqualTo(refreshToken);
    }
}
