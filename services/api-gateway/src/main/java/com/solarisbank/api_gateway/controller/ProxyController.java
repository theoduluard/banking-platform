package com.solarisbank.api_gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.Collections;

@RestController
public class ProxyController {

    private RestClient restClient = RestClient.create();

    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;

    @Value("${account.service.url:http://localhost:8082}")
    private String accountServiceUrl;

    @Value("${transaction.service.url:http://localhost:8083}")
    private String transactionServiceUrl;

    @Value("${messaging.service.url:http://localhost:8084}")
    private String messagingServiceUrl;

    @Value("${notification.service.url:http://localhost:8085}")
    private String notificationServiceUrl;

    @Value("${card.service.url:http://localhost:8086}")
    private String cardServiceUrl;

    @Value("${analytics.service.url:http://localhost:8087}")
    private String analyticsServiceUrl;

    @Value("${loan.service.url:http://localhost:8088}")
    private String loanServiceUrl;

    @Value("${fraud.service.url:http://localhost:8089}")
    private String fraudServiceUrl;

    @Value("${currency.service.url:http://localhost:8090}")
    private String currencyServiceUrl;

    @Value("${audit.service.url:http://localhost:8091}")
    private String auditServiceUrl;

    @Value("${document.service.url:http://localhost:8092}")
    private String documentServiceUrl;

    @RequestMapping("/api/v1/auth/**")
    public ResponseEntity<byte[]> proxyAuth(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, authServiceUrl);
    }

    @RequestMapping("/api/v1/accounts/**")
    public ResponseEntity<byte[]> proxyAccounts(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, accountServiceUrl);
    }

    @RequestMapping("/api/v1/beneficiaries/**")
    public ResponseEntity<byte[]> proxyBeneficiaries(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, accountServiceUrl);
    }

    @RequestMapping("/api/v1/transactions/**")
    public ResponseEntity<byte[]> proxyTransactions(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, transactionServiceUrl);
    }

    @RequestMapping("/api/v1/scheduled-transfers/**")
    public ResponseEntity<byte[]> proxyScheduledTransfers(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, transactionServiceUrl);
    }

    @RequestMapping("/api/v1/messages/**")
    public ResponseEntity<byte[]> proxyMessages(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, messagingServiceUrl);
    }

    @RequestMapping("/api/v1/requests/**")
    public ResponseEntity<byte[]> proxyRequests(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, messagingServiceUrl);
    }

    // ── Admin routes (gateway already enforced ADMIN role) ────────────────────

    @RequestMapping("/api/v1/admin/users/**")
    public ResponseEntity<byte[]> proxyAdminUsers(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, authServiceUrl);
    }

    @RequestMapping("/api/v1/admin/accounts/**")
    public ResponseEntity<byte[]> proxyAdminAccounts(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, accountServiceUrl);
    }

    @RequestMapping("/api/v1/admin/messages/**")
    public ResponseEntity<byte[]> proxyAdminMessages(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, messagingServiceUrl);
    }

    @RequestMapping("/api/v1/admin/requests/**")
    public ResponseEntity<byte[]> proxyAdminRequests(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, messagingServiceUrl);
    }

    @RequestMapping("/api/v1/admin/transactions/**")
    public ResponseEntity<byte[]> proxyAdminTransactions(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, transactionServiceUrl);
    }

    @RequestMapping("/api/v1/notifications/**")
    public ResponseEntity<byte[]> proxyNotifications(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, notificationServiceUrl);
    }

    @RequestMapping("/api/v1/cards/**")
    public ResponseEntity<byte[]> proxyCards(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, cardServiceUrl);
    }

    @RequestMapping("/api/v1/analytics/**")
    public ResponseEntity<byte[]> proxyAnalytics(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, analyticsServiceUrl);
    }

    @RequestMapping("/api/v1/loans/**")
    public ResponseEntity<byte[]> proxyLoans(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, loanServiceUrl);
    }

    @RequestMapping("/api/v1/fraud/**")
    public ResponseEntity<byte[]> proxyFraud(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, fraudServiceUrl);
    }

    @RequestMapping("/api/v1/currencies/**")
    public ResponseEntity<byte[]> proxyCurrencies(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, currencyServiceUrl);
    }

    @RequestMapping("/api/v1/audit/**")
    public ResponseEntity<byte[]> proxyAudit(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, auditServiceUrl);
    }

    @RequestMapping("/api/v1/documents/**")
    public ResponseEntity<byte[]> proxyDocuments(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, documentServiceUrl);
    }

    // ── Admin routes for new services ─────────────────────────────────────────

    @RequestMapping("/api/v1/admin/loans/**")
    public ResponseEntity<byte[]> proxyAdminLoans(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, loanServiceUrl);
    }

    @RequestMapping("/api/v1/admin/fraud/**")
    public ResponseEntity<byte[]> proxyAdminFraud(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, fraudServiceUrl);
    }

    @RequestMapping("/api/v1/admin/audit/**")
    public ResponseEntity<byte[]> proxyAdminAudit(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, auditServiceUrl);
    }

    private ResponseEntity<byte[]> forward(HttpServletRequest request, byte[] body, String targetBase) {
        String path  = request.getRequestURI();
        String query = request.getQueryString();
        String url   = targetBase + path + (query != null ? "?" + query : "");

        RestClient.RequestBodySpec spec = restClient
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(URI.create(url));

        // Forward all headers except host and gateway security headers (injected explicitly below)
        Collections.list(request.getHeaderNames()).stream()
                .filter(h -> !h.equalsIgnoreCase("host"))
                .filter(h -> !h.equalsIgnoreCase("X-User-Id"))
                .filter(h -> !h.equalsIgnoreCase("X-User-Role"))
                .forEach(h -> spec.header(h, request.getHeader(h)));

        // Inject gateway-authenticated user context exactly once (prevents duplication)
        String userId   = request.getHeader("X-User-Id");
        String userRole = request.getHeader("X-User-Role");
        if (userId   != null) spec.header("X-User-Id",   userId);
        if (userRole != null) spec.header("X-User-Role", userRole);

        if (body != null && body.length > 0) {
            spec.body(body);
        }

        try {
            return spec.exchange((req, res) -> {
                HttpHeaders headers = new HttpHeaders();
                res.getHeaders().forEach((key, values) -> {
                    if (!key.equalsIgnoreCase("Transfer-Encoding")) {
                        headers.addAll(key, values);
                    }
                });
                // res.getBody() is null for 204 No Content — guard against NPE
                var bodyStream = res.getBody();
                byte[] bodyBytes = (bodyStream != null) ? bodyStream.readAllBytes() : new byte[0];
                return ResponseEntity.status(res.getStatusCode())
                        .headers(headers)
                        .body(bodyBytes);
            });
        } catch (RestClientException e) {
            // Downstream service is unreachable — return a transparent 502 instead of
            // letting Spring Boot produce a generic 500 "Internal Server Error".
            String msg = "{\"status\":502,\"error\":\"Service unavailable\",\"detail\":\""
                    + e.getMessage().replace("\"", "'") + "\"}";
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .header("Content-Type", "application/json")
                    .body(msg.getBytes());
        }
    }
}
