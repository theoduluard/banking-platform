package com.solarisbank.auth_service.config;

import com.solarisbank.auth_service.model.User;
import com.solarisbank.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock private UserRepository  userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private DataInitializer dataInitializer;

    @BeforeEach
    void setUpCredentials() {
        // Inject @Value fields
        ReflectionTestUtils.setField(dataInitializer, "adminEmail",    "admin@solaris.demo");
        ReflectionTestUtils.setField(dataInitializer, "adminPassword", "Admin@1234!");
    }

    // ── run() — skip paths ────────────────────────────────────────────────────

    @Test
    void run_shouldSkip_whenAdminEmailIsBlank() throws Exception {
        ReflectionTestUtils.setField(dataInitializer, "adminEmail", "");

        dataInitializer.run(new DefaultApplicationArguments());

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void run_shouldSkip_whenAdminPasswordIsBlank() throws Exception {
        ReflectionTestUtils.setField(dataInitializer, "adminPassword", "");

        dataInitializer.run(new DefaultApplicationArguments());

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void run_shouldSkip_whenAdminAlreadyExists() throws Exception {
        when(userRepository.existsByEmail("admin@solaris.demo")).thenReturn(true);

        dataInitializer.run(new DefaultApplicationArguments());

        verify(userRepository).existsByEmail("admin@solaris.demo");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    // ── run() — create admin ──────────────────────────────────────────────────

    @Test
    void run_shouldCreateAdmin_whenCredentialsAreSetAndAdminDoesNotExist() throws Exception {
        when(userRepository.existsByEmail("admin@solaris.demo")).thenReturn(false);
        when(passwordEncoder.encode("Admin@1234!")).thenReturn("hashed_admin_pw");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        dataInitializer.run(new DefaultApplicationArguments());

        verify(userRepository).save(argThat(u ->
                u.getEmail().equals("admin@solaris.demo")
                && u.getRole() == User.Role.ADMIN
                && Boolean.TRUE.equals(u.getEmailVerified())
                && "hashed_admin_pw".equals(u.getPassword())
        ));
    }

    @Test
    void run_shouldEncodePassword_whenCreatingAdmin() throws Exception {
        when(userRepository.existsByEmail("admin@solaris.demo")).thenReturn(false);
        when(passwordEncoder.encode("Admin@1234!")).thenReturn("encoded");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        dataInitializer.run(new DefaultApplicationArguments());

        verify(passwordEncoder).encode("Admin@1234!");
    }
}
