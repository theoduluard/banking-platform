package com.solarisbank.api_gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

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

    @RequestMapping("/api/v1/admin/transactions/**")
    public ResponseEntity<byte[]> proxyAdminTransactions(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, transactionServiceUrl);
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

        return spec.exchange((req, res) -> {
            HttpHeaders headers = new HttpHeaders();
            res.getHeaders().forEach((key, values) -> {
                if (!key.equalsIgnoreCase("Transfer-Encoding")) {
                    headers.addAll(key, values);
                }
            });
            return ResponseEntity.status(res.getStatusCode())
                    .headers(headers)
                    .body(res.getBody().readAllBytes());
        });
    }
}
