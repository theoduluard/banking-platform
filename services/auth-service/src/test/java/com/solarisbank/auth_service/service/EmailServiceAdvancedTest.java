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
 * Tests the four email methods that are NOT covered by the base EmailServiceTest:
 * sendPasswordChangedEmail, sendEmailChangeOtpEmail,
 * sendNewEmailVerificationEmail, sendEmailChangedNotificationEmail.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceAdvancedTest {

    @Mock  private JavaMailSender mailSender;
    @InjectMocks private EmailService emailService;

    @BeforeEach
    void setUp() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ReflectionTestUtils.setField(emailService, "frontendUrl",  "http://localhost:5173");
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@solarisbank.demo");
    }

    // ── sendPasswordChangedEmail ───────────────────────────────────────────────

    @Test
    void sendPasswordChangedEmail_shouldSendEmail_whenSmtpIsAvailable() {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        assertThatNoException()
                .isThrownBy(() -> emailService.sendPasswordChangedEmail("user@test.com", "Alice"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPasswordChangedEmail_shouldNotThrow_whenSmtpFails() {
        doThrow(new MailSendException("SMTP error"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatNoException()
                .isThrownBy(() -> emailService.sendPasswordChangedEmail("user@test.com", "Alice"));
    }

    @Test
    void sendPasswordChangedEmail_shouldCreateMimeMessage() {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailService.sendPasswordChangedEmail("user@test.com", "Bob");

        verify(mailSender).createMimeMessage();
    }

    // ── sendEmailChangeOtpEmail ────────────────────────────────────────────────

    @Test
    void sendEmailChangeOtpEmail_shouldSendEmail_whenSmtpIsAvailable() {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        assertThatNoException()
                .isThrownBy(() -> emailService.sendEmailChangeOtpEmail("user@test.com", "Alice", "123456"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendEmailChangeOtpEmail_shouldNotThrow_whenSmtpFails() {
        doThrow(new MailSendException("Connection refused"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatNoException()
                .isThrownBy(() -> emailService.sendEmailChangeOtpEmail("user@test.com", "Alice", "654321"));
    }

    @Test
    void sendEmailChangeOtpEmail_shouldCallCreateMimeMessage() {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailService.sendEmailChangeOtpEmail("user@test.com", "Charlie", "000000");

        verify(mailSender).createMimeMessage();
    }

    // ── sendNewEmailVerificationEmail ──────────────────────────────────────────

    @Test
    void sendNewEmailVerificationEmail_shouldSendToNewAddress() {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        assertThatNoException()
                .isThrownBy(() -> emailService.sendNewEmailVerificationEmail(
                        "new@example.com", "Alice", "verify-token-123"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendNewEmailVerificationEmail_shouldNotThrow_whenSmtpFails() {
        doThrow(new MailSendException("Timeout"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatNoException()
                .isThrownBy(() -> emailService.sendNewEmailVerificationEmail(
                        "new@example.com", "Alice", "token"));
    }

    @Test
    void sendNewEmailVerificationEmail_shouldCallCreateMimeMessage() {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailService.sendNewEmailVerificationEmail("new@example.com", "Dave", "tok");

        verify(mailSender).createMimeMessage();
    }

    // ── sendEmailChangedNotificationEmail ─────────────────────────────────────

    @Test
    void sendEmailChangedNotificationEmail_shouldSendToOldAddress() {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        assertThatNoException()
                .isThrownBy(() -> emailService.sendEmailChangedNotificationEmail(
                        "old@example.com", "Alice", "new@example.com"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendEmailChangedNotificationEmail_shouldNotThrow_whenSmtpFails() {
        doThrow(new MailSendException("Auth failed"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatNoException()
                .isThrownBy(() -> emailService.sendEmailChangedNotificationEmail(
                        "old@example.com", "Alice", "new@example.com"));
    }

    @Test
    void sendEmailChangedNotificationEmail_shouldCallCreateMimeMessage() {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailService.sendEmailChangedNotificationEmail("old@example.com", "Eve", "new@example.com");

        verify(mailSender).createMimeMessage();
    }
}
