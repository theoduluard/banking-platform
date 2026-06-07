package com.solarisbank.document_service.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PdfGenerationServiceTest {

    private final PdfGenerationService service = new PdfGenerationService();

    // ── Basic output validity ──────────────────────────────────────────────────

    @Test
    void generateRib_withFullData_shouldReturnValidPdfBytes() throws IOException {
        Map<String, String> data = Map.of(
                "ownerName",     "Alice Dupont",
                "iban",          "FR76 3000 4028 3798 7654 3210 943",
                "bic",           "BNPAFRPPXXX",
                "accountNumber", "12345678"
        );

        byte[] pdf = service.generateRib(data);

        assertThat(pdf).isNotNull().isNotEmpty();
        // PDF magic bytes: %PDF
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generateRib_withEmptyData_shouldReturnValidPdfUsingDefaults() throws IOException {
        byte[] pdf = service.generateRib(Collections.emptyMap());

        assertThat(pdf).isNotNull().isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generateRib_outputShouldBeNonTrivialSize() throws IOException {
        byte[] pdf = service.generateRib(Map.of("ownerName", "Test User"));

        // A minimal PDFBox document with text is at least 1 KB
        assertThat(pdf.length).isGreaterThan(1024);
    }

    // ── getOrDefault fallbacks ─────────────────────────────────────────────────

    @Test
    void generateRib_missingOwnerName_shouldUseFallback() throws IOException {
        // Should not throw even when ownerName is absent
        byte[] pdf = service.generateRib(Map.of("iban", "FR76 XXXX"));

        assertThat(pdf).isNotNull().isNotEmpty();
    }

    @Test
    void generateRib_partialData_shouldNotThrow() throws IOException {
        // Only bic provided — all other fields use getOrDefault
        byte[] pdf = service.generateRib(Map.of("bic", "SLRSFRPPXXX"));

        assertThat(pdf).isNotNull().isNotEmpty();
    }
}
