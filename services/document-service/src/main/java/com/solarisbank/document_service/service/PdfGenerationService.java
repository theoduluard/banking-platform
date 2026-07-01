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

    // ── Page metrics ──────────────────────────────────────────────────────────
    private static final float PAGE_W   = PDRectangle.A4.getWidth();   // 595.28 pt
    private static final float PAGE_H   = PDRectangle.A4.getHeight();  // 841.89 pt
    private static final float MARGIN   = 48f;
    private static final float CONTENT_W = PAGE_W - 2 * MARGIN;

    // ── Brand colours (sRGB 0–1) ──────────────────────────────────────────────
    private static final float[] NAVY     = {0.043f, 0.086f, 0.157f}; // #0B1628
    private static final float[] BLUE     = {0.161f, 0.361f, 0.961f}; // #295CF5
    private static final float[] GOLD     = {0.839f, 0.698f, 0.255f}; // #D6B241
    private static final float[] BLUE_LT  = {0.937f, 0.949f, 1.000f}; // #EFF2FF
    private static final float[] GREY_BG  = {0.961f, 0.965f, 0.973f}; // #F5F6F8
    private static final float[] GREY_BD  = {0.878f, 0.886f, 0.902f}; // #E0E2E6
    private static final float[] TXT_DK   = {0.067f, 0.094f, 0.153f}; // #111827
    private static final float[] TXT_MD   = {0.420f, 0.447f, 0.502f}; // #6B7280
    private static final float[] TXT_LT   = {0.620f, 0.647f, 0.702f}; // #9EA5B2
    private static final float[] STEEL    = {0.380f, 0.480f, 0.640f}; // header subdued
    private static final float[] WHITE    = {1f, 1f, 1f};
    private static final float[] NOTE_BG  = {0.988f, 0.992f, 1.000f}; // #FCFCFF

    // ─────────────────────────────────────────────────────────────────────────

    public byte[] generateRib(Map<String, String> accountData) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType1Font bold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font italic  = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float bodyStartY = drawHeader(cs, bold, regular);
                drawBody(cs, bold, regular, italic, accountData, bodyStartY);
                drawFooter(cs, regular);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    /** Draws the full-width navy header and returns the Y just below the accent stripe. */
    private float drawHeader(PDPageContentStream cs,
                              PDType1Font bold, PDType1Font regular) throws IOException {
        final float H    = 100f;
        final float botY = PAGE_H - H;

        // Navy background
        fill(cs, NAVY);
        cs.addRect(0, botY, PAGE_W, H);
        cs.fill();

        // Blue accent stripe (3 pt) at the very bottom of the header
        fill(cs, BLUE);
        cs.addRect(0, botY, PAGE_W, 3f);
        cs.fill();

        // ── Solaris icon (44 × 44) ────────────────────────────────────────────
        final float ICON_SIZE = 44f;
        final float iconX = MARGIN;
        final float iconY = botY + (H - ICON_SIZE) / 2f;
        drawSolarisIcon(cs, iconX, iconY, ICON_SIZE);

        // "Solaris" wordmark
        fill(cs, WHITE);
        cs.setFont(bold, 19f);
        cs.beginText();
        cs.newLineAtOffset(iconX + ICON_SIZE + 10, iconY + 26);
        cs.showText("Solaris");
        cs.endText();

        fill(cs, GOLD);
        cs.setFont(bold, 8.5f);
        cs.beginText();
        cs.newLineAtOffset(iconX + ICON_SIZE + 11, iconY + 13);
        cs.showText("BANK");
        cs.endText();

        // Thin vertical rule
        stroke(cs, STEEL);
        cs.setLineWidth(0.8f);
        cs.moveTo(iconX + ICON_SIZE + 85, botY + 20);
        cs.lineTo(iconX + ICON_SIZE + 85, PAGE_H - 20);
        cs.stroke();

        // Title block
        float titleX = iconX + ICON_SIZE + 101;

        fill(cs, WHITE);
        cs.setFont(bold, 13.5f);
        cs.beginText();
        cs.newLineAtOffset(titleX, botY + 63);
        cs.showText("RELEVE D'IDENTITE BANCAIRE");
        cs.endText();

        fill(cs, STEEL);
        cs.setFont(regular, 8.5f);
        cs.beginText();
        cs.newLineAtOffset(titleX, botY + 47);
        cs.showText("Document officiel - valide sans signature ni cachet");
        cs.endText();

        // Emission date — right-aligned
        String dateLabel = "Emis le " + LocalDate.now()
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH));
        float dw = textWidth(regular, 8f, dateLabel);
        fill(cs, STEEL);
        cs.setFont(regular, 8f);
        cs.beginText();
        cs.newLineAtOffset(PAGE_W - MARGIN - dw, botY + 55);
        cs.showText(dateLabel);
        cs.endText();

        return botY - 3f; // bottom of accent stripe
    }

    // ── Body ──────────────────────────────────────────────────────────────────

    private void drawBody(PDPageContentStream cs,
                           PDType1Font bold, PDType1Font regular, PDType1Font italic,
                           Map<String, String> accountData, float startY) throws IOException {
        float y = startY - 38;

        // ─ Titulaire ─────────────────────────────────────────────────────────

        fill(cs, TXT_MD);
        cs.setFont(italic, 8f);
        cs.beginText();
        cs.newLineAtOffset(MARGIN, y);
        cs.showText("TITULAIRE DU COMPTE");
        cs.endText();

        y -= 26;
        fill(cs, TXT_DK);
        cs.setFont(bold, 24f);
        cs.beginText();
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(accountData.getOrDefault("ownerName", "N/A"));
        cs.endText();

        y -= 20;
        // Blue accent rule
        stroke(cs, BLUE);
        cs.setLineWidth(3.5f);
        cs.moveTo(MARGIN, y);
        cs.lineTo(MARGIN + 64, y);
        cs.stroke();

        y -= 46;

        // ─ Coordonnées bancaires — section label ──────────────────────────────

        fill(cs, TXT_MD);
        cs.setFont(italic, 8f);
        cs.beginText();
        cs.newLineAtOffset(MARGIN, y);
        cs.showText("COORDONNEES BANCAIRES");
        cs.endText();

        y -= 16;

        // ─ IBAN highlighted box ───────────────────────────────────────────────
        final float IBAN_BOX_H = 72f;

        fill(cs, BLUE_LT);
        cs.addRect(MARGIN, y - IBAN_BOX_H, CONTENT_W, IBAN_BOX_H);
        cs.fill();

        // Left blue accent bar
        fill(cs, BLUE);
        cs.addRect(MARGIN, y - IBAN_BOX_H, 4f, IBAN_BOX_H);
        cs.fill();

        // "IBAN" label
        fill(cs, BLUE);
        cs.setFont(italic, 7.5f);
        cs.beginText();
        cs.newLineAtOffset(MARGIN + 16, y - 17);
        cs.showText("IBAN");
        cs.endText();

        // IBAN value
        String iban = formatIban(accountData.getOrDefault("iban", "FR76 XXXX XXXX XXXX XXXX XXXX XXX"));
        fill(cs, TXT_DK);
        cs.setFont(bold, 15f);
        cs.beginText();
        cs.newLineAtOffset(MARGIN + 16, y - 45);
        cs.showText(iban);
        cs.endText();

        y -= IBAN_BOX_H + 8;

        // ─ Details card ───────────────────────────────────────────────────────
        final float CARD_H   = 132f;
        final float ROW_H    = 28f;
        final float ROW_PAD  = 22f;

        fill(cs, GREY_BG);
        cs.addRect(MARGIN, y - CARD_H, CONTENT_W, CARD_H);
        cs.fill();

        stroke(cs, GREY_BD);
        cs.setLineWidth(0.5f);
        cs.addRect(MARGIN, y - CARD_H, CONTENT_W, CARD_H);
        cs.stroke();

        // Left blue accent bar
        fill(cs, BLUE);
        cs.addRect(MARGIN, y - CARD_H, 4f, CARD_H);
        cs.fill();

        float rowY     = y - ROW_PAD;
        float labelX   = MARGIN + 16;
        float valueX   = MARGIN + 170;

        drawDetailRow(cs, italic, bold, labelX, valueX, rowY,
                "Banque", "Solaris Bank SA", GREY_BD);
        rowY -= ROW_H;
        drawDetailRow(cs, italic, bold, labelX, valueX, rowY,
                "BIC / SWIFT", accountData.getOrDefault("bic", "SLRSFRPPXXX"), GREY_BD);
        rowY -= ROW_H;
        drawDetailRow(cs, italic, bold, labelX, valueX, rowY,
                "N° de compte", accountData.getOrDefault("accountNumber", "N/A"), GREY_BD);
        rowY -= ROW_H;
        drawDetailRow(cs, italic, bold, labelX, valueX, rowY,
                "Etablissement", "Solaris Bank S.A. — Paris", null);

        y -= CARD_H + 28;

        // ─ Information note ───────────────────────────────────────────────────
        final float NOTE_H = 78f;

        fill(cs, NOTE_BG);
        cs.addRect(MARGIN, y - NOTE_H, CONTENT_W, NOTE_H);
        cs.fill();

        stroke(cs, GREY_BD);
        cs.setLineWidth(0.4f);
        cs.addRect(MARGIN, y - NOTE_H, CONTENT_W, NOTE_H);
        cs.stroke();

        // "i" icon — small blue circle
        fill(cs, BLUE);
        appendCircle(cs, MARGIN + 20, y - 20, 7f);
        cs.fill();
        fill(cs, WHITE);
        cs.setFont(bold, 9f);
        cs.beginText();
        cs.newLineAtOffset(MARGIN + 17f, y - 23f);
        cs.showText("i");
        cs.endText();

        // Note title
        fill(cs, TXT_DK);
        cs.setFont(bold, 9f);
        cs.beginText();
        cs.newLineAtOffset(MARGIN + 36, y - 17);
        cs.showText("Comment utiliser ce document ?");
        cs.endText();

        // Note body
        fill(cs, TXT_MD);
        cs.setFont(regular, 8f);
        cs.beginText();
        cs.newLineAtOffset(MARGIN + 36, y - 32);
        cs.showText("Ce document vous permet de communiquer vos coordonnees bancaires a un tiers (employeur,");
        cs.endText();
        cs.beginText();
        cs.newLineAtOffset(MARGIN + 36, y - 44);
        cs.showText("organismes sociaux, fournisseurs) pour la mise en place de virements ou de prelevements.");
        cs.endText();
        cs.beginText();
        cs.newLineAtOffset(MARGIN + 36, y - 56);
        cs.showText("Il est valide comme justificatif bancaire officiel.");
        cs.endText();
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private void drawFooter(PDPageContentStream cs, PDType1Font regular) throws IOException {
        final float SEP_Y = 72f;

        stroke(cs, GREY_BD);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN, SEP_Y);
        cs.lineTo(PAGE_W - MARGIN, SEP_Y);
        cs.stroke();

        // Small Solaris icon in footer
        drawSolarisIcon(cs, MARGIN, SEP_Y - 52, 24);

        fill(cs, TXT_LT);
        cs.setFont(regular, 6.8f);
        cs.beginText();
        cs.newLineAtOffset(MARGIN + 32, SEP_Y - 16);
        cs.showText("Ce document est genere automatiquement par Solaris Bank S.A. et constitue un justificatif officiel de coordonnees bancaires.");
        cs.endText();
        cs.beginText();
        cs.newLineAtOffset(MARGIN + 32, SEP_Y - 28);
        cs.showText("Solaris Bank S.A. · 12 rue de la Paix, 75001 Paris · SIREN 000 000 000 · Agree par l'ACPR (Autorite de Controle Prudentiel et de Resolution)");
        cs.endText();
    }

    // ── Solaris icon ──────────────────────────────────────────────────────────

    /**
     * Draws the Solaris sun-compass icon — a navy rounded square with an orbit ring,
     * 8 radiating rays, a gold centre disc and a white inner highlight.
     * Designed on a 40-unit grid; {@code size} scales proportionally.
     *
     * @param x    left of the bounding box (PDF coordinates — origin at page bottom-left)
     * @param y    bottom of the bounding box
     * @param size width = height in points
     */
    private void drawSolarisIcon(PDPageContentStream cs,
                                  float x, float y, float size) throws IOException {
        final float s  = size / 40f;
        final float cx = x + size / 2f;
        final float cy = y + size / 2f;

        // 1. Navy rounded-square background (corner radius = 10/40 × size)
        fill(cs, NAVY);
        appendRoundedRect(cs, x, y, size, size, 10 * s);
        cs.fill();

        // 2. Orbit ring (muted steel on navy ≈ white @ 20 %)
        stroke(cs, STEEL);
        cs.setLineWidth(0.9f * s);
        appendCircle(cs, cx, cy, 13 * s);
        cs.stroke();

        // 3. Eight radiating sun rays (cardinal = 2 pt, diagonal = 1.2 pt)
        cs.setStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
        int[] degrees = {0, 45, 90, 135, 180, 225, 270, 315};
        for (int i = 0; i < degrees.length; i++) {
            double rad   = Math.toRadians(degrees[i]);
            float  inner = 8.5f * s;
            float  outer = 13.5f * s;
            cs.setLineWidth((i % 2 == 0 ? 2.0f : 1.2f) * s);
            cs.moveTo(cx + inner * (float) Math.cos(rad), cy + inner * (float) Math.sin(rad));
            cs.lineTo(cx + outer * (float) Math.cos(rad), cy + outer * (float) Math.sin(rad));
            cs.stroke();
        }

        // 4. Gold centre disc
        fill(cs, GOLD);
        appendCircle(cs, cx, cy, 6 * s);
        cs.fill();

        // 5. Inner white highlight
        fill(cs, WHITE);
        appendCircle(cs, cx, cy, 2.8f * s);
        cs.fill();
    }

    // ── Path helpers ─────────────────────────────────────────────────────────

    /** Appends a rounded-rectangle path using 4 Bezier-approximated corner arcs. */
    private void appendRoundedRect(PDPageContentStream cs,
                                    float x, float y, float w, float h, float r) throws IOException {
        final float k = 0.5523f;
        cs.moveTo(x + r, y);
        cs.lineTo(x + w - r, y);
        cs.curveTo(x + w - r + k * r, y,              x + w,             y + r - k * r, x + w, y + r);
        cs.lineTo(x + w, y + h - r);
        cs.curveTo(x + w,             y + h - r + k * r, x + w - r + k * r, y + h,         x + w - r, y + h);
        cs.lineTo(x + r, y + h);
        cs.curveTo(x + r - k * r,     y + h,             x,                 y + h - r + k * r, x, y + h - r);
        cs.lineTo(x, y + r);
        cs.curveTo(x,                 y + r - k * r,     x + r - k * r,     y,                 x + r, y);
        cs.closePath();
    }

    /** Appends a full-circle path using 4 Bezier arcs. */
    private void appendCircle(PDPageContentStream cs, float cx, float cy, float r) throws IOException {
        final float k = 0.5523f;
        cs.moveTo(cx, cy - r);
        cs.curveTo(cx + k * r, cy - r,  cx + r,     cy - k * r, cx + r, cy);
        cs.curveTo(cx + r,     cy + k * r, cx + k * r, cy + r,     cx,     cy + r);
        cs.curveTo(cx - k * r, cy + r,  cx - r,     cy + k * r, cx - r, cy);
        cs.curveTo(cx - r,     cy - k * r, cx - k * r, cy - r,     cx,     cy - r);
        cs.closePath();
    }

    // ── Colour shorthands ─────────────────────────────────────────────────────

    private void fill(PDPageContentStream cs, float[] rgb) throws IOException {
        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
    }

    private void stroke(PDPageContentStream cs, float[] rgb) throws IOException {
        cs.setStrokingColor(rgb[0], rgb[1], rgb[2]);
    }

    // ── Text helpers ──────────────────────────────────────────────────────────

    private float textWidth(PDType1Font font, float size, String text) throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    /** Draws a labelled key/value row. Draws a divider below the row when {@code dividerColor} is non-null. */
    private void drawDetailRow(PDPageContentStream cs,
                                PDType1Font labelFont, PDType1Font valueFont,
                                float labelX, float valueX, float y,
                                String label, String value, float[] dividerColor) throws IOException {
        fill(cs, TXT_MD);
        cs.setFont(labelFont, 8.5f);
        cs.beginText();
        cs.newLineAtOffset(labelX, y);
        cs.showText(label);
        cs.endText();

        fill(cs, TXT_DK);
        cs.setFont(valueFont, 9.5f);
        cs.beginText();
        cs.newLineAtOffset(valueX, y);
        cs.showText(value);
        cs.endText();

        if (dividerColor != null) {
            stroke(cs, dividerColor);
            cs.setLineWidth(0.3f);
            cs.moveTo(labelX - 2, y - 9);
            cs.lineTo(PAGE_W - MARGIN - 10, y - 9);
            cs.stroke();
        }
    }

    /** Groups IBAN characters into blocks of 4 for readability. */
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
