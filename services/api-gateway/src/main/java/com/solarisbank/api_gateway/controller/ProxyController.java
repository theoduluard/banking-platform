package com.solarisbank.api_gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
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

    private final RestClient restClient = RestClient.create();

    private static final String AUTH_SERVICE    = "http://localhost:8081";
    private static final String ACCOUNT_SERVICE = "http://localhost:8082";

    @RequestMapping("/api/v1/auth/**")
    public ResponseEntity<byte[]> proxyAuth(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, AUTH_SERVICE);
    }

    @RequestMapping("/api/v1/accounts/**")
    public ResponseEntity<byte[]> proxyAccounts(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body, ACCOUNT_SERVICE);
    }

    private ResponseEntity<byte[]> forward(HttpServletRequest request, byte[] body, String targetBase) {
        String path  = request.getRequestURI();
        String query = request.getQueryString();
        String url   = targetBase + path + (query != null ? "?" + query : "");

        RestClient.RequestBodySpec spec = restClient
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(URI.create(url));

        Collections.list(request.getHeaderNames()).stream()
                .filter(h -> !h.equalsIgnoreCase("host"))
                .forEach(h -> spec.header(h, request.getHeader(h)));

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
