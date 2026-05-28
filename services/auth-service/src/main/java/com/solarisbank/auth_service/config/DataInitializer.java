package com.solarisbank.auth_service.config;

import com.solarisbank.auth_service.model.User;
import com.solarisbank.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("[DataInitializer] ADMIN_EMAIL / ADMIN_PASSWORD not set — skipping admin seed.");
            return;
        }

        if (userRepository.existsByEmail(adminEmail)) {
            log.info("[DataInitializer] Admin account already exists — skipping.");
            return;
        }

        User admin = User.builder()
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .firstname("Admin")
                .lastname("Solaris")
                .role(User.Role.ADMIN)
                .build();

        userRepository.save(admin);
        log.info("[DataInitializer] Admin account created: {}", adminEmail);
    }
}
