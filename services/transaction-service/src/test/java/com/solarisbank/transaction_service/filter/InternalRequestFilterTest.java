package com.solarisbank.transaction_service.filter;

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
    private static final String SECRET = "tx-internal-secret";

    @BeforeEach
    void setUp() {
        filter = new InternalRequestFilter();
        ReflectionTestUtils.setField(filter, "internalSecret", SECRET);
    }

    @Test
    void shouldNotFilter_actuator_returnsTrue() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/actuator/health");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilter_transactionApi_returnsFalse() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/transactions");
        assertThat(filter.shouldNotFilter(req)).isFalse();
    }

    @Test
    void doFilterInternal_validUserId_shouldPassThrough() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);
        req.addHeader("X-User-Id", UUID.randomUUID().toString());

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
    }

    @Test
    void doFilterInternal_validSecret_shouldPassThrough() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);
        req.addHeader("X-Internal-Secret", SECRET);

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
    }

    @Test
    void doFilterInternal_noHeaders_shouldReturn401() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Unauthorized");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_invalidUUID_shouldReturn401() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);
        req.addHeader("X-User-Id", "not-valid");

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_wrongSecret_shouldReturn401() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);
        req.addHeader("X-Internal-Secret", "bad-secret");

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void doFilterInternal_blankUserId_shouldReturn401() throws Exception {
        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        FilterChain             chain = mock(FilterChain.class);
        req.addHeader("X-User-Id", "");

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
    }
}
