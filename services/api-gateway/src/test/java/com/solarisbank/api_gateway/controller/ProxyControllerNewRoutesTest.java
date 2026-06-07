package com.solarisbank.api_gateway.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Coverage for proxy routes added after the initial batch:
 * notifications, cards, analytics, loans, fraud, currencies, audit, documents,
 * and their corresponding admin routes (admin/loans, admin/fraud, admin/audit).
 */
class ProxyControllerNewRoutesTest {

    private MockMvc mockMvc;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        RestClient testClient = RestClient.builder(restTemplate).build();

        ProxyController controller = new ProxyController();
        ReflectionTestUtils.setField(controller, "restClient",            testClient);
        ReflectionTestUtils.setField(controller, "authServiceUrl",         "http://localhost:8081");
        ReflectionTestUtils.setField(controller, "accountServiceUrl",      "http://localhost:8082");
        ReflectionTestUtils.setField(controller, "transactionServiceUrl",  "http://localhost:8083");
        ReflectionTestUtils.setField(controller, "messagingServiceUrl",    "http://localhost:8084");
        ReflectionTestUtils.setField(controller, "notificationServiceUrl", "http://localhost:8085");
        ReflectionTestUtils.setField(controller, "cardServiceUrl",         "http://localhost:8086");
        ReflectionTestUtils.setField(controller, "analyticsServiceUrl",    "http://localhost:8087");
        ReflectionTestUtils.setField(controller, "loanServiceUrl",         "http://localhost:8088");
        ReflectionTestUtils.setField(controller, "fraudServiceUrl",        "http://localhost:8089");
        ReflectionTestUtils.setField(controller, "currencyServiceUrl",     "http://localhost:8090");
        ReflectionTestUtils.setField(controller, "auditServiceUrl",        "http://localhost:8091");
        ReflectionTestUtils.setField(controller, "documentServiceUrl",     "http://localhost:8092");

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ── Proxy → notification-service ──────────────────────────────────────────

    @Test
    void proxyNotifications_shouldForwardGet_toNotificationService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8085/api/v1/notifications"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    @Test
    void proxyNotifications_shouldForwardPatch_toNotificationService() throws Exception {
        UUID userId  = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8085/api/v1/notifications/" + notifId + "/read"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess("{\"read\":true}", MediaType.APPLICATION_JSON));

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notifId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    // ── Proxy → card-service ───────────────────────────────────────────────────

    @Test
    void proxyCards_shouldForwardGet_toCardService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8086/api/v1/cards"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/cards")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    @Test
    void proxyCards_shouldForwardPost_toCardService() throws Exception {
        UUID userId     = UUID.randomUUID();
        UUID accountId  = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8086/api/v1/cards"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"cardId\":\"card-123\"}"));

        mockMvc.perform(post("/api/v1/cards")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + accountId + "\",\"type\":\"VIRTUAL\"}"))
                .andExpect(status().isCreated());

        mockServer.verify();
    }

    // ── Proxy → analytics-service ──────────────────────────────────────────────

    @Test
    void proxyAnalytics_shouldForwardGet_toAnalyticsService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8087/api/v1/analytics/spending"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"total\":0}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/analytics/spending")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    // ── Proxy → loan-service ───────────────────────────────────────────────────

    @Test
    void proxyLoans_shouldForwardGet_toLoanService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8088/api/v1/loans"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/loans")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    @Test
    void proxyLoans_shouldForwardPost_toCreateLoan() throws Exception {
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8088/api/v1/loans"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"loanId\":\"loan-456\"}"));

        mockMvc.perform(post("/api/v1/loans")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000,\"termMonths\":24}"))
                .andExpect(status().isCreated());

        mockServer.verify();
    }

    // ── Proxy → fraud-service ──────────────────────────────────────────────────

    @Test
    void proxyFraud_shouldForwardGet_toFraudService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8089/api/v1/fraud/alerts"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/fraud/alerts")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    // ── Proxy → currency-service ───────────────────────────────────────────────

    @Test
    void proxyCurrencies_shouldForwardGet_toCurrencyService() throws Exception {
        mockServer.expect(requestTo("http://localhost:8090/api/v1/currencies"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"code\":\"EUR\"}]", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/currencies"))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    @Test
    void proxyCurrencies_shouldForwardGetWithQueryString_toCurrencyService() throws Exception {
        mockServer.expect(requestTo("http://localhost:8090/api/v1/currencies/convert?from=EUR&to=USD&amount=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"result\":108.5}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/currencies/convert?from=EUR&to=USD&amount=100"))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    // ── Proxy → audit-service ──────────────────────────────────────────────────

    @Test
    void proxyAudit_shouldForwardGet_toAuditService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8091/api/v1/audit/events"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/audit/events")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    // ── Proxy → document-service ───────────────────────────────────────────────

    @Test
    void proxyDocuments_shouldForwardGet_toDocumentService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8092/api/v1/documents"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/documents")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    @Test
    void proxyDocuments_shouldForwardPost_toDocumentService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8092/api/v1/documents"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"documentId\":\"doc-789\"}"));

        mockMvc.perform(post("/api/v1/documents")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ID_CARD\"}"))
                .andExpect(status().isCreated());

        mockServer.verify();
    }

    // ── Proxy → admin routes (new services) ───────────────────────────────────

    @Test
    void proxyAdminLoans_shouldForwardGet_toLoanService() throws Exception {
        mockServer.expect(requestTo("http://localhost:8088/api/v1/admin/loans"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/admin/loans")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    @Test
    void proxyAdminFraud_shouldForwardGet_toFraudService() throws Exception {
        mockServer.expect(requestTo("http://localhost:8089/api/v1/admin/fraud"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/admin/fraud")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    @Test
    void proxyAdminAudit_shouldForwardGet_toAuditService() throws Exception {
        mockServer.expect(requestTo("http://localhost:8091/api/v1/admin/audit"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/admin/audit")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    // ── Header forwarding — new services ──────────────────────────────────────

    @Test
    void proxyCards_shouldForwardXUserIdHeader_toCardService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8086/api/v1/cards"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .header("X-User-Id", userId.toString()))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/cards")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    @Test
    void proxyLoans_shouldForwardXUserRoleHeader_toAdminLoanService() throws Exception {
        mockServer.expect(requestTo("http://localhost:8088/api/v1/admin/loans/approve"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .header("X-User-Role", "ADMIN"))
                .andRespond(withSuccess("{\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));

        mockMvc.perform(patch("/api/v1/admin/loans/approve")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        mockServer.verify();
    }
}
