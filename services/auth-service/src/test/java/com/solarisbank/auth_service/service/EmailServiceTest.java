package com.solarisbank.auth_service.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests EmailService with a real MimeMessage (null-session) so MimeMessageHelper
 * can set headers without an SMTP server, and a mocked JavaMailSender to control
 * send() behaviour.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @InjectMocks private EmailService emailService;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        // Real MimeMessage backed by a no-op session — MimeMessageHelper can set all
        // fields without touching any SMTP infrastructure.
        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Inject @Value fields (normally wired by Spring)
        ReflectionTestUtils.setField(emailService, "frontendUrl",  "http://localhost:5173");
        ReflectionTestUtils.setField(emailService, "fromAddress",  "noreply@solarisbank.demo");
    }

    // ── sendVerificationEmail ──────────────────────────────────────────────────

    @Test
    void sendVerificationEmail_shouldSendEmail_whenSmtpIsAvailable() {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailService.sendVerificationEmail("user@example.com", "Alice", "token-abc");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendVerificationEmail_shouldNotThrow_whenSmtpThrowsMailException() {
        doThrow(new MailSendException("SMTP down"))
                .when(mailSender).send(any(MimeMessage.class));

        // Error is caught and logged — must not propagate
        assertThatNoException().isThrownBy(() ->
                emailService.sendVerificationEmail("user@example.com", "Alice", "token-abc"));
    }

    @Test
    void sendVerificationEmail_shouldCallCreateMimeMessage() {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailService.sendVerificationEmail("user@example.com", "Bob", "tok-123");

        verify(mailSender).createMimeMessage();
    }

    // ── sendPasswordResetEmail ─────────────────────────────────────────────────

    @Test
    void sendPasswordResetEmail_shouldSendEmail_whenSmtpIsAvailable() {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailService.sendPasswordResetEmail("user@example.com", "Alice", "reset-tok");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPasswordResetEmail_shouldNotThrow_whenSmtpFails() {
        doThrow(new MailSendException("Connection refused"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatNoException().isThrownBy(() ->
                emailService.sendPasswordResetEmail("user@example.com", "Alice", "reset-tok"));
    }

    @Test
    void sendPasswordResetEmail_shouldCallCreateMimeMessage() {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailService.sendPasswordResetEmail("user@example.com", "Charlie", "r-tok-456");

        verify(mailSender).createMimeMessage();
    }

    // ── sendOtpEmail ──────────────────────────────────────────────────────────

    @Test
    void sendOtpEmail_shouldSendEmail_whenSmtpIsAvailable() {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailService.sendOtpEmail("user@example.com", "Alice", "123456");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendOtpEmail_shouldNotThrow_whenSmtpFails() {
        doThrow(new MailSendException("Auth failed"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatNoException().isThrownBy(() ->
                emailService.sendOtpEmail("user@example.com", "Alice", "654321"));
    }

    @Test
    void sendOtpEmail_shouldCallCreateMimeMessage() {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailService.sendOtpEmail("user@example.com", "Dave", "000000");

        verify(mailSender).createMimeMessage();
    }
}
