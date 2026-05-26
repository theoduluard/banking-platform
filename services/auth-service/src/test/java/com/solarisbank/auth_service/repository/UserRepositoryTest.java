package com.solarisbank.auth_service.repository;

import com.solarisbank.auth_service.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("alice@example.com")
                .password("encoded_password")
                .firstname("Alice")
                .lastname("Smith")
                .role(User.Role.CLIENT)
                .build();
    }

    // ── findByEmail ────────────────────────────────────────────────────────────

    @Test
    void findByEmail_shouldReturnUser_whenEmailExists() {
        // Arrange
        entityManager.persistAndFlush(user);

        // Act
        Optional<User> result = userRepository.findByEmail("alice@example.com");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("alice@example.com");
        assertThat(result.get().getFirstname()).isEqualTo("Alice");
        assertThat(result.get().getRole()).isEqualTo(User.Role.CLIENT);
    }

    @Test
    void findByEmail_shouldReturnEmpty_whenEmailDoesNotExist() {
        // Act
        Optional<User> result = userRepository.findByEmail("unknown@example.com");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void findByEmail_isCaseSensitive() {
        // Arrange
        entityManager.persistAndFlush(user);

        // Act
        Optional<User> result = userRepository.findByEmail("ALICE@EXAMPLE.COM");

        // Assert — H2 string equality is case-sensitive by default
        assertThat(result).isEmpty();
    }

    // ── existsByEmail ──────────────────────────────────────────────────────────

    @Test
    void existsByEmail_shouldReturnTrue_whenEmailExists() {
        // Arrange
        entityManager.persistAndFlush(user);

        // Act & Assert
        assertThat(userRepository.existsByEmail("alice@example.com")).isTrue();
    }

    @Test
    void existsByEmail_shouldReturnFalse_whenEmailDoesNotExist() {
        // Act & Assert
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    // ── @PrePersist lifecycle ──────────────────────────────────────────────────

    @Test
    void prePersist_shouldSetCreatedAtAndIsActive() {
        // Act
        entityManager.persistAndFlush(user);
        entityManager.clear();

        User persisted = userRepository.findByEmail("alice@example.com").orElseThrow();

        // Assert
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getIsActive()).isTrue();
    }

    // ── unique constraint ──────────────────────────────────────────────────────

    @Test
    void save_shouldFail_whenDuplicateEmail() {
        // Arrange
        entityManager.persistAndFlush(user);

        User duplicate = User.builder()
                .email("alice@example.com")   // same email
                .password("another_password")
                .firstname("Bob")
                .lastname("Jones")
                .role(User.Role.CLIENT)
                .build();

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> entityManager.persistAndFlush(duplicate)
        );
    }
}
