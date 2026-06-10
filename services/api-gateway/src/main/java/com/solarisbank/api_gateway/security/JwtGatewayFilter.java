package com.solarisbank.api_gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtGatewayFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            // Logout is public so the refresh token cookie can be revoked even when
            // the access token has already expired.  No JWT auth is needed — the
            // refresh token cookie is the credential being invalidated.
            "/api/v1/auth/logout",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            // OTP flow — user has no JWT yet at this point in the login sequence;
            // the session token is passed in the request body, not in Authorization.
            "/api/v1/auth/verify-otp",
            "/api/v1/auth/resend-otp",
            // Email-change step 3 — user clicks a link from their inbox (no JWT).
            "/api/v1/auth/verify-new-email",
            // Currency rates and conversion are public — no personal data involved.
            // The frontend shows exchange rates before login (e.g. on the home page).
            "/api/v1/currencies/rates",
            "/api/v1/currencies/convert",
            // Loan simulation is public — users can estimate repayments before signing up.
            "/api/v1/loans/simulate",
            // Prometheus scraping and health probes — internal traffic only
            "/actuator/prometheus",
            "/actuator/health"
    );

    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin/";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);

        // Validate JWT — only wrap JWT-parsing code in the catch so that
        // downstream proxy errors are never swallowed as "Token is invalid".
        String userId;
        String role;
        try {
            if (!jwtService.isTokenValid(token)) {
                writeError(response, HttpStatus.UNAUTHORIZED, "Token is expired or invalid");
                return;
            }
            userId = jwtService.extractUserId(token);
            role   = jwtService.extractRole(token);
        } catch (Exception e) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Token is invalid");
            return;
        }

        // Block admin paths for non-admin users
        if (path.startsWith(ADMIN_PATH_PREFIX) && !"ADMIN".equals(role)) {
            writeError(response, HttpStatus.FORBIDDEN, "Access denied: admin only");
            return;
        }

        filterChain.doFilter(new EnrichedRequestWrapper(request, userId, role), response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":" + status.value() + ",\"error\":\"" + message + "\"}");
    }

    // ── Request wrapper that injects X-User-Id and X-User-Role headers ────────

    private static class EnrichedRequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String> extraHeaders = new HashMap<>();

        public EnrichedRequestWrapper(HttpServletRequest request, String userId, String role) {
            super(request);
            extraHeaders.put("X-User-Id",   userId);
            extraHeaders.put("X-User-Role", role != null ? role : "");
        }

        @Override
        public String getHeader(String name) {
            // Case-insensitive lookup so X-User-Id, x-user-id, etc. all match
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return Collections.enumeration(List.of(entry.getValue()));
                }
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            extraHeaders.keySet().forEach(k -> {
                // Case-insensitive check: prevents adding X-User-Id when x-user-id already exists
                boolean alreadyPresent = names.stream().anyMatch(n -> n.equalsIgnoreCase(k));
                if (!alreadyPresent) names.add(k);
            });
            return Collections.enumeration(names);
        }
    }
}
