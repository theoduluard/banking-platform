package com.solarisbank.document_service.service;

import com.solarisbank.document_service.model.GeneratedDocument;
import com.solarisbank.document_service.repository.GeneratedDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private GeneratedDocumentRepository docRepo;

    @Mock
    private PdfGenerationService pdfService;

    private DocumentService documentService;

    private final UUID USER_ID    = UUID.randomUUID();
    private final UUID ACCOUNT_ID = UUID.randomUUID();
    private final byte[] FAKE_PDF = "%PDF-1.4 fake content".getBytes();

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(docRepo, pdfService);
        when(docRepo.save(any(GeneratedDocument.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── generateRib ───────────────────────────────────────────────────────────

    @Test
    void generateRib_accountServiceUnavailable_shouldUsePlaceholderDataAndReturnPdf()
            throws IOException {
        // pdfService is mocked — the real RestClient call will fail (no server),
        // triggering the catch block with placeholder data.
        when(pdfService.generateRib(anyMap())).thenReturn(FAKE_PDF);

        byte[] result = documentService.generateRib(USER_ID, ACCOUNT_ID, "test-secret");

        assertThat(result).isEqualTo(FAKE_PDF);

        // Verify pdfService was called with at least the BIC key
        ArgumentCaptor<Map> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pdfService).generateRib(dataCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, String> passedData = dataCaptor.getValue();
        assertThat(passedData).containsKey("bic");
        assertThat(passedData.get("bic")).isEqualTo("SLRSFRPPXXX");
    }

    @Test
    void generateRib_shouldPersistDocumentRecordInRepo() throws IOException {
        when(pdfService.generateRib(anyMap())).thenReturn(FAKE_PDF);

        documentService.generateRib(USER_ID, ACCOUNT_ID, "test-secret");

        ArgumentCaptor<GeneratedDocument> captor = ArgumentCaptor.forClass(GeneratedDocument.class);
        verify(docRepo).save(captor.capture());
        GeneratedDocument doc = captor.getValue();

        assertThat(doc.getUserId()).isEqualTo(USER_ID);
        assertThat(doc.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(doc.getDocumentType()).isEqualTo("RIB");
        assertThat(doc.getFilename()).startsWith("RIB_");
        assertThat(doc.getFilename()).endsWith(".pdf");
        assertThat(doc.getGeneratedAt()).isNotNull();
    }

    @Test
    void generateRib_filenameShouldContainAccountIdPrefix() throws IOException {
        when(pdfService.generateRib(anyMap())).thenReturn(FAKE_PDF);

        documentService.generateRib(USER_ID, ACCOUNT_ID, "test-secret");

        ArgumentCaptor<GeneratedDocument> captor = ArgumentCaptor.forClass(GeneratedDocument.class);
        verify(docRepo).save(captor.capture());
        String expectedPrefix = ACCOUNT_ID.toString().substring(0, 8).toUpperCase();
        assertThat(captor.getValue().getFilename()).contains(expectedPrefix);
    }

    // ── getDocumentsForUser ────────────────────────────────────────────────────

    @Test
    void getDocumentsForUser_shouldDelegateToRepository() {
        GeneratedDocument doc = GeneratedDocument.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .accountId(ACCOUNT_ID)
                .documentType("RIB")
                .filename("RIB_ABC12345_20250601_120000.pdf")
                .generatedAt(LocalDateTime.now())
                .build();

        when(docRepo.findByUserIdOrderByGeneratedAtDesc(USER_ID)).thenReturn(List.of(doc));

        List<GeneratedDocument> result = documentService.getDocumentsForUser(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDocumentType()).isEqualTo("RIB");
        verify(docRepo).findByUserIdOrderByGeneratedAtDesc(USER_ID);
    }

    @Test
    void getDocumentsForUser_empty_shouldReturnEmptyList() {
        when(docRepo.findByUserIdOrderByGeneratedAtDesc(USER_ID)).thenReturn(List.of());

        List<GeneratedDocument> result = documentService.getDocumentsForUser(USER_ID);

        assertThat(result).isEmpty();
    }
}
