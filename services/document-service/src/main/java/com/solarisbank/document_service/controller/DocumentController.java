package com.solarisbank.document_service.controller;

import com.solarisbank.document_service.model.GeneratedDocument;
import com.solarisbank.document_service.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @Value("${internal.secret:changeme-dev-only}")
    private String internalSecret;

    @GetMapping("/rib/{accountId}")
    public ResponseEntity<byte[]> downloadRib(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID accountId) throws IOException {

        byte[] pdf = documentService.generateRib(userId, accountId, internalSecret);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment",
                "RIB_" + accountId.toString().substring(0, 8).toUpperCase() + ".pdf");
        headers.setContentLength(pdf.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
    }

    @GetMapping("/history")
    public ResponseEntity<List<GeneratedDocument>> getHistory(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(documentService.getDocumentsForUser(userId));
    }
}
