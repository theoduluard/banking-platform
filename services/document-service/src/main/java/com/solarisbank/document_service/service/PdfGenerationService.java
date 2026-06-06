package com.solarisbank.document_service.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@Slf4j
public class PdfGenerationService {

    public byte[] generateRib(Map<String, String> accountData) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType1Font fontBold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {

                float margin = 50f;
                float pageWidth  = PDRectangle.A4.getWidth();
                float pageHeight = PDRectangle.A4.getHeight();
                float y = pageHeight - margin;

                // ── Header ───────────────────────────────────────────────────
                cs.setFont(fontBold, 20);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Solaris Bank");
                cs.endText();

                y -= 14;
                cs.setFont(fontRegular, 10);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Relevé d'Identité Bancaire (RIB)");
                cs.endText();

                y -= 8;
                // Divider line
                cs.setLineWidth(0.5f);
                cs.moveTo(margin, y);
                cs.lineTo(pageWidth - margin, y);
                cs.stroke();

                y -= 30;

                // ── Account holder ───────────────────────────────────────────
                writeRow(cs, fontBold, fontRegular, margin, y, "Titulaire du compte :", accountData.getOrDefault("ownerName", "N/A"));
                y -= 20;
                writeRow(cs, fontBold, fontRegular, margin, y, "Date de génération :", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

                y -= 35;

                // ── Bank details ─────────────────────────────────────────────
                cs.setFont(fontBold, 12);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Coordonnées bancaires");
                cs.endText();

                y -= 20;
                writeRow(cs, fontBold, fontRegular, margin, y, "Banque :", "Solaris Bank SA");
                y -= 20;
                writeRow(cs, fontBold, fontRegular, margin, y, "Code BIC :", accountData.getOrDefault("bic", "SLRSFRPPXXX"));
                y -= 20;
                writeRow(cs, fontBold, fontRegular, margin, y, "IBAN :", accountData.getOrDefault("iban", "FR76 XXXX XXXX XXXX XXXX XXXX XXX"));
                y -= 20;
                writeRow(cs, fontBold, fontRegular, margin, y, "N° de compte :", accountData.getOrDefault("accountNumber", "N/A"));

                y -= 40;

                // ── Footer notice ────────────────────────────────────────────
                cs.setLineWidth(0.5f);
                cs.moveTo(margin, y);
                cs.lineTo(pageWidth - margin, y);
                cs.stroke();

                y -= 15;
                cs.setFont(fontRegular, 8);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Ce document est généré automatiquement par Solaris Bank. Il est valable comme justificatif de coordonnées bancaires.");
                cs.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private void writeRow(PDPageContentStream cs, PDType1Font labelFont, PDType1Font valueFont,
                          float x, float y, String label, String value) throws IOException {
        cs.setFont(labelFont, 11);
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(label);
        cs.endText();

        cs.setFont(valueFont, 11);
        cs.beginText();
        cs.newLineAtOffset(x + 180, y);
        cs.showText(value);
        cs.endText();
    }
}
