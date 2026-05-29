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
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
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

        try {
            if (!jwtService.isTokenValid(token)) {
                writeError(response, HttpStatus.UNAUTHORIZED, "Token is expired or invalid");
                return;
            }

            String userId = jwtService.extractUserId(token);
            String role   = jwtService.extractRole(token);

            // Block admin paths for non-admin users
            if (path.startsWith(ADMIN_PATH_PREFIX) && !"ADMIN".equals(role)) {
                writeError(response, HttpStatus.FORBIDDEN, "Access denied: admin only");
                return;
            }

            filterChain.doFilter(new EnrichedRequestWrapper(request, userId, role), response);

        } catch (Exception e) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Token is invalid");
        }
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
            String override = extraHeaders.get(name);
            if (override != null) return override;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String override = extraHeaders.get(name);
            if (override != null) return Collections.enumeration(List.of(override));
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            extraHeaders.keySet().forEach(k -> {
                if (!names.contains(k)) names.add(k);
            });
            return Collections.enumeration(names);
        }
    }
}
