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

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProxyControllerTest {

    private MockMvc mockMvc;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        // Créer un RestTemplate + MockRestServiceServer pour intercepter les appels HTTP
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        // Créer un RestClient adossé au RestTemplate mocké
        RestClient testClient = RestClient.builder(restTemplate).build();

        // Injecter le RestClient de test dans le contrôleur via réflexion
        ProxyController controller = new ProxyController();
        ReflectionTestUtils.setField(controller, "restClient", testClient);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ── Proxy → auth-service ───────────────────────────────────────────────────

    @Test
    void proxyAuth_shouldForwardPost_toAuthService() throws Exception {
        mockServer.expect(requestTo("http://localhost:8081/api/v1/auth/register"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"userId\":\"abc-123\"}"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jean@solaris.com\",\"password\":\"Pass@123\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().string("{\"userId\":\"abc-123\"}"));

        mockServer.verify();
    }

    @Test
    void proxyAuth_shouldForwardLogin_toAuthService() throws Exception {
        mockServer.expect(requestTo("http://localhost:8081/api/v1/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"accessToken\":\"jwt-token\"}",
                        MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jean@solaris.com\",\"password\":\"Pass@123\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"accessToken\":\"jwt-token\"}"));

        mockServer.verify();
    }

    // ── Proxy → account-service ────────────────────────────────────────────────

    @Test
    void proxyAccounts_shouldForwardGet_toAccountService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/accounts"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/accounts")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    @Test
    void proxyAccounts_shouldForwardPost_toAccountService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/accounts"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"iban\":\"FR7630006000010000000000197\"}"));

        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CHECKING\"}"))
                .andExpect(status().isCreated());

        mockServer.verify();
    }

    @Test
    void proxyAccounts_shouldForwardXUserIdHeader_toDownstream() throws Exception {
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/accounts"))
                .andExpect(header("X-User-Id", userId.toString()))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/accounts")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    // ── Proxy → transaction-service ────────────────────────────────────────────

    @Test
    void proxyTransactions_shouldForwardGet_toTransactionService() throws Exception {
        UUID accountId = UUID.randomUUID();

        mockServer.expect(requestTo(
                "http://localhost:8083/api/v1/transactions?accountId=" + accountId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));

        // Use query string in the URL directly so MockMvc populates request.getQueryString()
        mockMvc.perform(get("/api/v1/transactions?accountId=" + accountId))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    @Test
    void proxyTransactions_shouldForwardPost_toTransactionService() throws Exception {
        UUID userId        = UUID.randomUUID();
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId   = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8083/api/v1/transactions/transfer"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"PENDING\"}"));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"" + fromAccountId + "\","
                                + "\"toAccountId\":\"" + toAccountId + "\","
                                + "\"amount\":100.00}"))
                .andExpect(status().isAccepted());

        mockServer.verify();
    }

    // ── Comportement du proxy ──────────────────────────────────────────────────

    @Test
    void proxy_shouldForwardDownstreamStatusCode() throws Exception {
        UUID accountId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/accounts/" + accountId))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Account not found\"}"));

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId))
                .andExpect(status().isNotFound());

        mockServer.verify();
    }

    @Test
    void proxy_shouldForwardRequestWithoutBody_whenBodyIsAbsent() throws Exception {
        mockServer.expect(requestTo("http://localhost:8082/api/v1/accounts"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // GET sans body — ne doit pas échouer
        mockMvc.perform(get("/api/v1/accounts")
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        mockServer.verify();
    }
}
