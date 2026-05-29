package com.solarisbank.api_gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Sliding-window rate limiter on POST /api/v1/auth/login.
 *
 * <p>Each IP address is allowed at most {@value #MAX_REQUESTS} login attempts
 * per {@value #WINDOW_MS} ms (1 minute). Excess requests receive HTTP 429.
 *
 * <p>Implementation:
 * <ul>
 *   <li>A {@link ConcurrentHashMap} maps each IP to a {@link ConcurrentLinkedDeque}
 *       of request timestamps (epoch ms).</li>
 *   <li>On every request, timestamps older than {@value #WINDOW_MS} ms are evicted
 *       from the front of the deque (sliding window).</li>
 *   <li>If the deque size reaches {@value #MAX_REQUESTS}, the request is rejected.</li>
 * </ul>
 *
 * <p>Runs with {@code @Order(1)} — before {@code JwtGatewayFilter} — so abusive
 * clients are rejected immediately without JWT validation overhead.
 *
 * <p>The real client IP is read from {@code X-Forwarded-For} (set by nginx),
 * falling back to {@code request.getRemoteAddr()}.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH   = "/api/v1/auth/login";
    private static final int    MAX_REQUESTS = 10;
    private static final long   WINDOW_MS    = 60_000L; // 1 minute

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> requestWindows
            = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Only rate-limit POST /api/v1/auth/login
        if (!"POST".equalsIgnoreCase(request.getMethod())
                || !LOGIN_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip  = resolveClientIp(request);
        long   now = System.currentTimeMillis();

        ConcurrentLinkedDeque<Long> window =
                requestWindows.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());

        // Evict timestamps that have fallen outside the sliding window
        while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
            window.pollFirst();
        }

        if (window.size() >= MAX_REQUESTS) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too many login attempts. Please wait before trying again.\"}");
            return;
        }

        window.addLast(now);
        filterChain.doFilter(request, response);
    }

    /**
     * Resolves the real client IP.
     * Takes only the first (leftmost) value from X-Forwarded-For to prevent
     * header-spoofing attacks where a client injects a fake IP.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
