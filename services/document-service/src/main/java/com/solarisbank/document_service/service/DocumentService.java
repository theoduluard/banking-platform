package com.solarisbank.document_service.service;

import com.solarisbank.document_service.model.GeneratedDocument;
import com.solarisbank.document_service.repository.GeneratedDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final GeneratedDocumentRepository docRepo;
    private final PdfGenerationService pdfService;
    private final RestClient restClient = RestClient.create();

    @Value("${account.service.url:http://localhost:8082}")
    private String accountServiceUrl;

    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;

    @Transactional
    public byte[] generateRib(UUID userId, UUID accountId, String internalSecret) throws IOException {
        // Fetch account details from account-service
        Map<String, String> accountData = new HashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> account = restClient.get()
                    .uri(accountServiceUrl + "/api/v1/accounts/" + accountId)
                    .header("X-User-Id", userId.toString())
                    .header("X-Internal-Secret", internalSecret)
                    .retrieve()
                    .body(Map.class);

            if (account != null) {
                accountData.put("iban", String.valueOf(account.getOrDefault("iban", "FR76 XXXX XXXX XXXX XXXX XXXX XXX")));
                accountData.put("accountNumber", String.valueOf(account.getOrDefault("accountNumber", accountId.toString().substring(0, 8).toUpperCase())));
            }
        } catch (Exception e) {
            log.warn("Could not fetch account details for RIB — using placeholder data: {}", e.getMessage());
            accountData.put("iban", "FR76 XXXX XXXX XXXX XXXX XXXX XXX");
            accountData.put("accountNumber", accountId.toString().substring(0, 8).toUpperCase());
        }

        // Fetch user name from auth-service
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> userInfo = restClient.get()
                    .uri(authServiceUrl + "/api/v1/internal/users/" + userId)
                    .header("X-Internal-Secret", internalSecret)
                    .retrieve()
                    .body(Map.class);
            if (userInfo != null && userInfo.get("fullName") != null) {
                accountData.put("ownerName", String.valueOf(userInfo.get("fullName")));
            } else {
                accountData.put("ownerName", "N/A");
            }
        } catch (Exception e) {
            log.warn("Could not fetch user info for RIB — falling back to N/A: {}", e.getMessage());
            accountData.put("ownerName", "N/A");
        }
        accountData.put("bic", "SLRSFRPPXXX");

        byte[] pdf = pdfService.generateRib(accountData);

        // Record the generation in the audit log
        String filename = "RIB_" + accountId.toString().substring(0, 8).toUpperCase()
                + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf";
        docRepo.save(GeneratedDocument.builder()
                .userId(userId)
                .accountId(accountId)
                .documentType("RIB")
                .filename(filename)
                .generatedAt(LocalDateTime.now())
                .build());

        return pdf;
    }

    public List<GeneratedDocument> getDocumentsForUser(UUID userId) {
        return docRepo.findByUserIdOrderByGeneratedAtDesc(userId);
    }
}
