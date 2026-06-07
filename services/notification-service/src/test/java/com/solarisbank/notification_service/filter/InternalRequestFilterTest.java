package com.solarisbank.notification_service.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InternalRequestFilterTest {

    private InternalRequestFilter filter;
    private static final String SECRET = "test-internal-secret";

    @BeforeEach
    void setUp() {
        filter = new InternalRequestFilter();
        ReflectionTestUtils.setField(filter, "internalSecret", SECRET);
    }

    // ── shouldNotFilter ────────────────────────────────────────────────────────

    @Test
    void shouldNotFilter_actuatorPath_returnsTrue() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/actuator/health");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilter_nonActuatorPath_returnsFalse() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/notifications");
        assertThat(filter.shouldNotFilter(req)).isFalse();
    }

    // ── doFilterInternal — allowed ─────────────────────────────────────────────

    @Test
    void doFilterInternal_withValidUserId_shouldPassThrough() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);

        req.addHeader("X-User-Id", UUID.randomUUID().toString());
        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isNotEqualTo(401);
    }

    @Test
    void doFilterInternal_withValidSecret_shouldPassThrough() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);

        req.addHeader("X-Internal-Secret", SECRET);
        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
    }

    // ── doFilterInternal — rejected ────────────────────────────────────────────

    @Test
    void doFilterInternal_withNoHeaders_shouldReturn401() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Unauthorized");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_withInvalidUUID_shouldReturn401() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);

        req.addHeader("X-User-Id", "bad-uuid");
        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void doFilterInternal_withWrongSecret_shouldReturn401() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);

        req.addHeader("X-Internal-Secret", "nope");
        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }
}
