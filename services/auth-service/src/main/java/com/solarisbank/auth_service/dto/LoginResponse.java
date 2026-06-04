package com.solarisbank.auth_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Login / refresh response sent to the client.
 * Fix 14+15: {@code refreshToken} is populated internally (so the controller can
 * set it as an HttpOnly cookie) but is then nulled out before serialization.
 * {@code @JsonInclude(NON_NULL)} ensures the field is absent from the JSON body
 * rather than appearing as {@code "refreshToken": null}.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    private String accessToken;
    /** Set by the server as an HttpOnly cookie — never present in the JSON response body. */
    private String refreshToken;
    private String email;
    private String firstname;
    private String lastname;
    private String role;
}
