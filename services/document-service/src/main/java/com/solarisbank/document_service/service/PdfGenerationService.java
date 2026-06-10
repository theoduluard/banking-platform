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
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class PdfGenerationService {

    private static final float MARGIN       = 50f;
    private static final float CARD_PADDING = 16f;

    public byte[] generateRib(Map<String, String> accountData) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType1Font fontBold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontOblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            float pageWidth  = PDRectangle.A4.getWidth();
            float pageHeight = PDRectangle.A4.getHeight();

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {

                // ── Dark header band ─────────────────────────────────────────
                cs.setNonStrokingColor(0.11f, 0.11f, 0.15f);   // near-black
                cs.addRect(0, pageHeight - 90, pageWidth, 90);
                cs.fill();

                // Bank name (white)
                cs.setNonStrokingColor(1f, 1f, 1f);
                cs.setFont(fontBold, 22);
                cs.beginText();
                cs.newLineAtOffset(MARGIN, pageHeight - 48);
                cs.showText("Solaris Bank");
                cs.endText();

                // Subtitle (light grey)
                cs.setNonStrokingColor(0.75f, 0.75f, 0.80f);
                cs.setFont(fontRegular, 10);
                cs.beginText();
                cs.newLineAtOffset(MARGIN, pageHeight - 68);
                cs.showText("Relevé d'Identité Bancaire  ·  RIB");
                cs.endText();

                // Date (top-right, white)
                String dateStr = LocalDate.now()
                        .format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH));
                cs.setNonStrokingColor(0.75f, 0.75f, 0.80f);
                cs.setFont(fontRegular, 9);
                float dateWidth = fontRegular.getStringWidth("Émis le " + dateStr) / 1000f * 9;
                cs.beginText();
                cs.newLineAtOffset(pageWidth - MARGIN - dateWidth, pageHeight - 58);
                cs.showText("Émis le " + dateStr);
                cs.endText();

                // ── Titulaire section ────────────────────────────────────────
                float y = pageHeight - 130;
                cs.setNonStrokingColor(0f, 0f, 0f);

                cs.setFont(fontOblique, 8);
                cs.setNonStrokingColor(0.45f, 0.45f, 0.50f);
                cs.beginText();
                cs.newLineAtOffset(MARGIN, y);
                cs.showText("TITULAIRE DU COMPTE");
                cs.endText();

                y -= 18;
                cs.setFont(fontBold, 16);
                cs.setNonStrokingColor(0.11f, 0.11f, 0.15f);
                cs.beginText();
                cs.newLineAtOffset(MARGIN, y);
                cs.showText(accountData.getOrDefault("ownerName", "N/A"));
                cs.endText();

                // ── Thin accent rule ─────────────────────────────────────────
                y -= 18;
                cs.setStrokingColor(0.16f, 0.45f, 0.96f);   // brand blue
                cs.setLineWidth(2f);
                cs.moveTo(MARGIN, y);
                cs.lineTo(MARGIN + 60, y);
                cs.stroke();

                // ── Coordonnées card ─────────────────────────────────────────
                y -= 30;
                float cardTop    = y + 10;
                float cardHeight = 120f;
                float cardLeft   = MARGIN;
                float cardRight  = pageWidth - MARGIN;

                // Light grey background
                cs.setNonStrokingColor(0.97f, 0.97f, 0.98f);
                cs.addRect(cardLeft, cardTop - cardHeight, cardRight - cardLeft, cardHeight);
                cs.fill();

                // Left accent bar
                cs.setNonStrokingColor(0.16f, 0.45f, 0.96f);
                cs.addRect(cardLeft, cardTop - cardHeight, 4f, cardHeight);
                cs.fill();

                // Section title
                cs.setNonStrokingColor(0.45f, 0.45f, 0.50f);
                cs.setFont(fontOblique, 8);
                float ty = cardTop - CARD_PADDING - 2;
                cs.beginText();
                cs.newLineAtOffset(cardLeft + CARD_PADDING + 6, ty);
                cs.showText("COORDONNÉES BANCAIRES");
                cs.endText();

                ty -= 18;
                writeCardRow(cs, fontBold, fontRegular, cardLeft + CARD_PADDING + 6, ty,
                        "Banque",         "Solaris Bank SA");
                ty -= 18;
                writeCardRow(cs, fontBold, fontRegular, cardLeft + CARD_PADDING + 6, ty,
                        "BIC / SWIFT",    accountData.getOrDefault("bic", "SLRSFRPPXXX"));
                ty -= 18;
                writeCardRow(cs, fontBold, fontRegular, cardLeft + CARD_PADDING + 6, ty,
                        "IBAN",           formatIban(accountData.getOrDefault("iban", "FR76 XXXX XXXX XXXX XXXX XXXX XXX")));
                ty -= 18;
                writeCardRow(cs, fontBold, fontRegular, cardLeft + CARD_PADDING + 6, ty,
                        "N° de compte",   accountData.getOrDefault("accountNumber", "N/A"));

                // ── Legal notice at bottom ───────────────────────────────────
                float footerY = 60f;
                cs.setStrokingColor(0.85f, 0.85f, 0.88f);
                cs.setLineWidth(0.5f);
                cs.moveTo(MARGIN, footerY + 14);
                cs.lineTo(pageWidth - MARGIN, footerY + 14);
                cs.stroke();

                cs.setNonStrokingColor(0.55f, 0.55f, 0.60f);
                cs.setFont(fontRegular, 7.5f);
                cs.beginText();
                cs.newLineAtOffset(MARGIN, footerY);
                cs.showText("Ce document est généré automatiquement par Solaris Bank S.A. et constitue un justificatif officiel de coordonnées bancaires.");
                cs.endText();
                cs.beginText();
                cs.newLineAtOffset(MARGIN, footerY - 11);
                cs.showText("Solaris Bank S.A. · 12 rue de la Paix, 75001 Paris · SIREN 000 000 000 · Agréée par l'ACPR");
                cs.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private void writeCardRow(PDPageContentStream cs, PDType1Font labelFont,
                               PDType1Font valueFont, float x, float y,
                               String label, String value) throws IOException {
        cs.setFont(labelFont, 9);
        cs.setNonStrokingColor(0.45f, 0.45f, 0.50f);
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(label);
        cs.endText();

        cs.setFont(valueFont, 10);
        cs.setNonStrokingColor(0.11f, 0.11f, 0.15f);
        cs.beginText();
        cs.newLineAtOffset(x + 110, y);
        cs.showText(value);
        cs.endText();
    }

    /** Groups IBAN digits into blocks of 4 separated by spaces for readability. */
    private String formatIban(String iban) {
        String clean = iban.replace(" ", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clean.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append(' ');
            sb.append(clean.charAt(i));
        }
        return sb.toString();
    }
}
