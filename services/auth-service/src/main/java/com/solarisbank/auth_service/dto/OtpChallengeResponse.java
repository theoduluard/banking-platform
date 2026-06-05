package com.solarisbank.auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Returned by POST /login when 2FA is required.
 * The frontend stores the sessionToken, navigates to /verify-otp,
 * and submits it together with the 6-digit code.
 */
@Data
@AllArgsConstructor
public class OtpChallengeResponse {
    private final String status = "OTP_REQUIRED";
    private String sessionToken;
}
