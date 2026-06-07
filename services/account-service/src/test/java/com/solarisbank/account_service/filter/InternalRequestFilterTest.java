package com.solarisbank.account_service.filter;

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
    void shouldNotFilter_actuatorSubPath_returnsTrue() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/actuator/prometheus");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilter_apiPath_returnsFalse() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/accounts");
        assertThat(filter.shouldNotFilter(req)).isFalse();
    }

    // ── doFilterInternal — allowed ─────────────────────────────────────────────

    @Test
    void doFilterInternal_withValidUserId_shouldCallChain() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);

        req.addHeader("X-User-Id", UUID.randomUUID().toString());

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isNotEqualTo(401);
    }

    @Test
    void doFilterInternal_withValidInternalSecret_shouldCallChain() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);

        req.addHeader("X-Internal-Secret", SECRET);

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
    }

    @Test
    void doFilterInternal_withBothHeaders_shouldCallChain() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);

        req.addHeader("X-User-Id", UUID.randomUUID().toString());
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
        assertThat(res.getContentType()).isEqualTo("application/json");
        assertThat(res.getContentAsString()).contains("Unauthorized");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_withInvalidUserId_shouldReturn401() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);

        req.addHeader("X-User-Id", "not-a-uuid");

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_withWrongSecret_shouldReturn401() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);

        req.addHeader("X-Internal-Secret", "wrong-secret");

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_withBlankUserId_shouldReturn401() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);

        req.addHeader("X-User-Id", "   ");

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
    }
}
