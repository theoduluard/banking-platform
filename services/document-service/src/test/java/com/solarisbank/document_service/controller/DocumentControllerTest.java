package com.solarisbank.document_service.controller;

import com.solarisbank.document_service.model.GeneratedDocument;
import com.solarisbank.document_service.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DocumentController.class)
class DocumentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean DocumentService documentService;

    private static final UUID USER_ID    = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    private static final byte[] FAKE_PDF = "%PDF-1.4 fake".getBytes();

    // ── GET /rib/{accountId} ───────────────────────────────────────────────────

    @Test
    void downloadRib_shouldReturn200WithPdfContentType() throws Exception {
        when(documentService.generateRib(eq(USER_ID), eq(ACCOUNT_ID), anyString()))
                .thenReturn(FAKE_PDF);

        mockMvc.perform(get("/api/v1/documents/rib/{accountId}", ACCOUNT_ID)
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".pdf")));

        verify(documentService).generateRib(eq(USER_ID), eq(ACCOUNT_ID), anyString());
    }

    @Test
    void downloadRib_contentDispositionShouldContainAccountIdPrefix() throws Exception {
        when(documentService.generateRib(eq(USER_ID), eq(ACCOUNT_ID), anyString()))
                .thenReturn(FAKE_PDF);

        String expectedPrefix = ACCOUNT_ID.toString().substring(0, 8).toUpperCase();

        mockMvc.perform(get("/api/v1/documents/rib/{accountId}", ACCOUNT_ID)
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(expectedPrefix)));
    }

    @Test
    void downloadRib_shouldReturnCorrectContentLength() throws Exception {
        when(documentService.generateRib(any(UUID.class), any(UUID.class), anyString()))
                .thenReturn(FAKE_PDF);

        mockMvc.perform(get("/api/v1/documents/rib/{accountId}", ACCOUNT_ID)
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(header().longValue("Content-Length", FAKE_PDF.length));
    }

    // ── GET /history ───────────────────────────────────────────────────────────

    @Test
    void getHistory_shouldReturn200WithDocumentList() throws Exception {
        GeneratedDocument doc = GeneratedDocument.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .accountId(ACCOUNT_ID)
                .documentType("RIB")
                .filename("RIB_ABCD1234_20250601_120000.pdf")
                .generatedAt(LocalDateTime.now())
                .build();

        when(documentService.getDocumentsForUser(USER_ID)).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/v1/documents/history")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentType").value("RIB"))
                .andExpect(jsonPath("$[0].filename").value("RIB_ABCD1234_20250601_120000.pdf"));

        verify(documentService).getDocumentsForUser(USER_ID);
    }

    @Test
    void getHistory_empty_shouldReturn200EmptyArray() throws Exception {
        when(documentService.getDocumentsForUser(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/documents/history")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
