package com.solarisbank.auth_service.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    /** Public-facing URL of the frontend — used to build the verification link. */
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /** The "from" address shown to recipients. */
    @Value("${spring.mail.from:${spring.mail.username:noreply@solarisbank.demo}}")
    private String fromAddress;

    /**
     * Sends an HTML verification email with a link containing the token.
     * If sending fails (SMTP not configured in dev), the URL is logged instead.
     */
    public void sendVerificationEmail(String toEmail, String firstname, String token) {
        String verificationUrl = frontendUrl + "/verify-email?token=" + token;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Vérifiez votre adresse email — Solaris Bank");
            helper.setText(buildHtml(firstname, verificationUrl), true);

            mailSender.send(message);
            log.info("[Email] Verification email sent to {}", toEmail);

        } catch (MessagingException | org.springframework.mail.MailException e) {
            // In development the SMTP may not be configured — log the URL so
            // developers can still test the flow without an actual mail server.
            log.warn("[Email] Could not send email to {} ({}). Verification URL: {}",
                    toEmail, e.getMessage(), verificationUrl);
        }
    }

    /**
     * Sends an HTML password-reset email.
     * Falls back to logging the URL when SMTP is not configured (development).
     */
    public void sendPasswordResetEmail(String toEmail, String firstname, String token) {
        String resetUrl = frontendUrl + "/reset-password?token=" + token;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Réinitialisation de votre mot de passe — Solaris Bank");
            helper.setText(buildResetHtml(firstname, resetUrl), true);

            mailSender.send(message);
            log.info("[Email] Password reset email sent to {}", toEmail);

        } catch (MessagingException | org.springframework.mail.MailException e) {
            log.warn("[Email] Could not send reset email to {} ({}). Reset URL: {}",
                    toEmail, e.getMessage(), resetUrl);
        }
    }

    /**
     * Sends the 6-digit OTP code for 2FA login verification.
     * Falls back to logging the code when SMTP is not configured (development).
     */
    public void sendOtpEmail(String toEmail, String firstname, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Votre code de vérification — Solaris Bank");
            helper.setText(buildOtpHtml(firstname, code), true);

            mailSender.send(message);
            log.info("[Email] OTP email sent to {}", toEmail);

        } catch (MessagingException | org.springframework.mail.MailException e) {
            log.warn("[Email] Could not send OTP email to {} ({}). Code: {}",
                    toEmail, e.getMessage(), code);
        }
    }

    // ── HTML templates ────────────────────────────────────────────────────────

    private String buildHtml(String firstname, String verificationUrl) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0;padding:0;background:#f4f4f5;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:40px 20px;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.06);">

                    <!-- Header -->
                    <tr>
                      <td style="background:#1d4ed8;padding:32px 40px;text-align:center;">
                        <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;letter-spacing:-0.5px;">
                          Solaris Bank
                        </h1>
                      </td>
                    </tr>

                    <!-- Body -->
                    <tr>
                      <td style="padding:40px;">
                        <p style="margin:0 0 16px;font-size:15px;color:#111827;">Bonjour <strong>%s</strong>,</p>
                        <p style="margin:0 0 24px;font-size:15px;color:#374151;line-height:1.6;">
                          Merci de vous être inscrit sur Solaris Bank.<br>
                          Cliquez sur le bouton ci-dessous pour activer votre compte.
                          Ce lien est valable <strong>24 heures</strong>.
                        </p>

                        <!-- CTA Button -->
                        <table cellpadding="0" cellspacing="0" style="margin:0 auto 32px;">
                          <tr>
                            <td style="border-radius:8px;background:#1d4ed8;">
                              <a href="%s"
                                 style="display:block;padding:14px 32px;color:#ffffff;font-size:15px;
                                        font-weight:600;text-decoration:none;border-radius:8px;">
                                Vérifier mon email
                              </a>
                            </td>
                          </tr>
                        </table>

                        <p style="margin:0 0 8px;font-size:13px;color:#6b7280;">
                          Si le bouton ne fonctionne pas, copiez ce lien dans votre navigateur :
                        </p>
                        <p style="margin:0;font-size:12px;color:#1d4ed8;word-break:break-all;">
                          <a href="%s" style="color:#1d4ed8;">%s</a>
                        </p>
                      </td>
                    </tr>

                    <!-- Footer -->
                    <tr>
                      <td style="background:#f9fafb;padding:20px 40px;border-top:1px solid #e5e7eb;">
                        <p style="margin:0;font-size:12px;color:#9ca3af;text-align:center;">
                          Si vous n'avez pas créé de compte, ignorez simplement cet email.<br>
                          © Solaris Bank — Démo technique
                        </p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(firstname, verificationUrl, verificationUrl, verificationUrl);
    }

    private String buildOtpHtml(String firstname, String code) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0;padding:0;background:#f4f4f5;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:40px 20px;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.06);">

                    <!-- Header -->
                    <tr>
                      <td style="background:#1d4ed8;padding:32px 40px;text-align:center;">
                        <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;letter-spacing:-0.5px;">
                          Solaris Bank
                        </h1>
                      </td>
                    </tr>

                    <!-- Body -->
                    <tr>
                      <td style="padding:40px;">
                        <p style="margin:0 0 16px;font-size:15px;color:#111827;">Bonjour <strong>%s</strong>,</p>
                        <p style="margin:0 0 24px;font-size:15px;color:#374151;line-height:1.6;">
                          Voici votre code de vérification pour vous connecter à Solaris Bank.<br>
                          Ce code est valable <strong>10 minutes</strong>.
                        </p>

                        <!-- OTP Code -->
                        <table cellpadding="0" cellspacing="0" style="margin:0 auto 32px;">
                          <tr>
                            <td style="border-radius:12px;background:#f0f4ff;border:2px solid #1d4ed8;
                                       padding:20px 40px;text-align:center;">
                              <span style="font-size:36px;font-weight:700;color:#1d4ed8;
                                           letter-spacing:12px;font-family:monospace;">%s</span>
                            </td>
                          </tr>
                        </table>

                        <p style="margin:0;font-size:13px;color:#6b7280;">
                          Si vous n'avez pas tenté de vous connecter, ignorez cet email et
                          votre compte restera sécurisé.
                        </p>
                      </td>
                    </tr>

                    <!-- Footer -->
                    <tr>
                      <td style="background:#f9fafb;padding:20px 40px;border-top:1px solid #e5e7eb;">
                        <p style="margin:0;font-size:12px;color:#9ca3af;text-align:center;">
                          Ne partagez jamais ce code avec quelqu'un.<br>
                          © Solaris Bank — Démo technique
                        </p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(firstname, code);
    }

    private String buildResetHtml(String firstname, String resetUrl) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0;padding:0;background:#f4f4f5;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:40px 20px;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.06);">

                    <!-- Header -->
                    <tr>
                      <td style="background:#1d4ed8;padding:32px 40px;text-align:center;">
                        <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;letter-spacing:-0.5px;">
                          Solaris Bank
                        </h1>
                      </td>
                    </tr>

                    <!-- Body -->
                    <tr>
                      <td style="padding:40px;">
                        <p style="margin:0 0 16px;font-size:15px;color:#111827;">Bonjour <strong>%s</strong>,</p>
                        <p style="margin:0 0 24px;font-size:15px;color:#374151;line-height:1.6;">
                          Nous avons reçu une demande de réinitialisation du mot de passe associé à votre compte.<br>
                          Cliquez sur le bouton ci-dessous pour choisir un nouveau mot de passe.
                          Ce lien est valable <strong>1 heure</strong>.
                        </p>

                        <!-- CTA Button -->
                        <table cellpadding="0" cellspacing="0" style="margin:0 auto 32px;">
                          <tr>
                            <td style="border-radius:8px;background:#1d4ed8;">
                              <a href="%s"
                                 style="display:block;padding:14px 32px;color:#ffffff;font-size:15px;
                                        font-weight:600;text-decoration:none;border-radius:8px;">
                                Réinitialiser mon mot de passe
                              </a>
                            </td>
                          </tr>
                        </table>

                        <p style="margin:0 0 8px;font-size:13px;color:#6b7280;">
                          Si le bouton ne fonctionne pas, copiez ce lien dans votre navigateur :
                        </p>
                        <p style="margin:0 0 24px;font-size:12px;color:#1d4ed8;word-break:break-all;">
                          <a href="%s" style="color:#1d4ed8;">%s</a>
                        </p>
                        <p style="margin:0;font-size:13px;color:#6b7280;">
                          Si vous n'avez pas demandé cette réinitialisation, ignorez simplement cet email.
                          Votre mot de passe reste inchangé.
                        </p>
                      </td>
                    </tr>

                    <!-- Footer -->
                    <tr>
                      <td style="background:#f9fafb;padding:20px 40px;border-top:1px solid #e5e7eb;">
                        <p style="margin:0;font-size:12px;color:#9ca3af;text-align:center;">
                          © Solaris Bank — Démo technique
                        </p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(firstname, resetUrl, resetUrl, resetUrl);
    }
}
