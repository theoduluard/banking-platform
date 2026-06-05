package com.solarisbank.api_gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    // ── Non-targeted paths pass straight through ───────────────────────────────

    @Test
    void shouldPassThrough_forNonLoginPath() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("GET", "/api/v1/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull(); // next filter was called
    }

    @Test
    void shouldPassThrough_forGetLoginPath() throws Exception {
        // Rate-limiting only applies to POST; GET /login should pass through
        MockHttpServletRequest  request  = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    // ── Requests within the limit pass through ────────────────────────────────

    @Test
    void shouldPassThrough_forFirst10LoginRequests() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest  request  = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setRemoteAddr("192.168.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain         chain    = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus())
                    .as("Request %d should pass through", i + 1)
                    .isEqualTo(200);
            assertThat(chain.getRequest()).isNotNull();
        }
    }

    // ── 11th request is rejected with 429 ─────────────────────────────────────

    @Test
    void shouldReturn429_whenRateLimitExceeded() throws Exception {
        // Fill the window to the limit
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr("10.0.0.1");
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        // The 11th attempt must be blocked
        MockHttpServletRequest  request  = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(chain.getRequest()).isNull();   // next filter was NOT called
        assertThat(response.getContentAsString()).contains("429");
    }

    // ── IP resolution — X-Forwarded-For ───────────────────────────────────────

    @Test
    void shouldUseXForwardedFor_asClientIp() throws Exception {
        String forwardedIp = "1.2.3.4";

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.addHeader("X-Forwarded-For", forwardedIp);
            req.setRemoteAddr("172.16.0.100"); // different IP — must be ignored
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        // 11th request from same forwarded IP → 429
        MockHttpServletRequest  request  = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", forwardedIp);
        request.setRemoteAddr("172.16.0.100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void shouldTakeFirstIp_fromXForwardedForChain() throws Exception {
        // X-Forwarded-For: client, proxy1, proxy2 — only the leftmost IP counts
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.addHeader("X-Forwarded-For", "9.9.9.9, 10.10.10.10, 11.11.11.11");
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        // 11th with the same first IP → 429
        MockHttpServletRequest  request  = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", "9.9.9.9, 192.0.2.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    // ── IP resolution — RemoteAddr fallback ───────────────────────────────────

    @Test
    void shouldUseRemoteAddr_whenXForwardedForIsAbsent() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr("5.6.7.8"); // no X-Forwarded-For header
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletRequest  request  = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("5.6.7.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    // ── Independent windows per IP ────────────────────────────────────────────

    @Test
    void shouldHaveIndependentWindows_forDifferentIps() throws Exception {
        // Exhaust the window for IP-A
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr("200.0.0.1");
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        // IP-B's window is still fresh — must pass
        MockHttpServletRequest  request  = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("200.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    // ── Sliding-window eviction ───────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void shouldEvictExpiredTimestamps_andAllowRequest() throws Exception {
        // Pre-load the window for a known IP with 10 timestamps that are 2 minutes old
        String ip = "evict-test-" + System.nanoTime(); // unique per test run
        ConcurrentLinkedDeque<Long> staleWindow = new ConcurrentLinkedDeque<>();
        long twoMinutesAgo = System.currentTimeMillis() - 120_000L;
        for (int i = 0; i < 10; i++) {
            staleWindow.addLast(twoMinutesAgo);
        }
        ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> windows =
                (ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>>)
                ReflectionTestUtils.getField(filter, "requestWindows");
        windows.put(ip, staleWindow);

        // The next request should evict all stale entries and succeed
        MockHttpServletRequest  request  = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);  // not rate-limited
        assertThat(chain.getRequest()).isNotNull();
        assertThat(staleWindow).hasSize(1); // stale entries gone, new one added
    }
}
