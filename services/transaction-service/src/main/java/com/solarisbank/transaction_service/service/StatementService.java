package com.solarisbank.transaction_service.service;

import com.solarisbank.transaction_service.client.AccountClient;
import com.solarisbank.transaction_service.client.dto.AccountResponse;
import com.solarisbank.transaction_service.model.Transaction;
import com.solarisbank.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatementService {

    private static final float MARGIN      = 50f;
    private static final float PAGE_WIDTH  = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter SHORT_DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TransactionRepository transactionRepository;
    private final AccountClient         accountClient;

    @Transactional(readOnly = true)
    public byte[] generateStatement(UUID accountId, UUID userId) {
        // Ownership check — throws 404 if the account doesn't belong to this user
        AccountResponse account = accountClient.getAccount(accountId, userId);

        List<Transaction> transactions =
                transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
                        accountId, accountId);

        try {
            return buildPdf(account, accountId, transactions);
        } catch (IOException e) {
            log.error("Failed to generate PDF statement for account {}", accountId, e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    // ── PDF generation ─────────────────────────────────────────────────────────

    private byte[] buildPdf(AccountResponse account, UUID accountId,
                             List<Transaction> transactions) throws IOException {

        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDType1Font fontBold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                float y = PAGE_HEIGHT - MARGIN;

                // ── Header bar ───────────────────────────────────────────────
                cs.setNonStrokingColor(0.10f, 0.25f, 0.55f);  // dark blue
                cs.addRect(MARGIN, y - 8, PAGE_WIDTH - 2 * MARGIN, 40);
                cs.fill();

                cs.setNonStrokingColor(1f, 1f, 1f);  // white text
                cs.beginText();
                cs.setFont(fontBold, 18);
                cs.newLineAtOffset(MARGIN + 10, y + 6);
                cs.showText("SolarisBank");
                cs.endText();

                cs.setNonStrokingColor(1f, 1f, 1f);
                cs.beginText();
                cs.setFont(fontRegular, 10);
                cs.newLineAtOffset(PAGE_WIDTH - MARGIN - 120, y + 6);
                cs.showText("RELEVE DE COMPTE");
                cs.endText();

                y -= 60;

                // ── Generation date ───────────────────────────────────────
                cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
                cs.beginText();
                cs.setFont(fontRegular, 9);
                cs.newLineAtOffset(MARGIN, y);
                cs.showText("Document genere le : " + LocalDateTime.now().format(DATE_FMT));
                cs.endText();

                y -= 25;

                // ── Account details box ───────────────────────────────────
                cs.setNonStrokingColor(0.95f, 0.97f, 1f);
                cs.addRect(MARGIN, y - 60, PAGE_WIDTH - 2 * MARGIN, 70);
                cs.fill();

                cs.setStrokingColor(0.80f, 0.88f, 1f);
                cs.addRect(MARGIN, y - 60, PAGE_WIDTH - 2 * MARGIN, 70);
                cs.stroke();

                cs.setNonStrokingColor(0.10f, 0.25f, 0.55f);
                cs.beginText();
                cs.setFont(fontBold, 11);
                cs.newLineAtOffset(MARGIN + 12, y - 8);
                cs.showText("Informations du compte");
                cs.endText();

                String accountType = "CHECKING".equals(account.getType()) ? "Compte courant" : "Compte epargne";
                String currency    = account.getCurrency() != null ? account.getCurrency() : "EUR";
                String balanceFmt  = formatAmount(account.getBalance(), currency);

                cs.setNonStrokingColor(0.2f, 0.2f, 0.2f);
                String[] labels = { "IBAN", "Type", "Solde actuel", "Devise" };
                String[] values = { account.getIban(), accountType, balanceFmt, currency };

                for (int i = 0; i < labels.length; i++) {
                    float lx = MARGIN + 12 + (i % 2) * 235;
                    float ly = y - 28 - (i / 2) * 16;

                    cs.beginText();
                    cs.setFont(fontBold, 8);
                    cs.newLineAtOffset(lx, ly);
                    cs.showText(labels[i] + " : ");
                    cs.endText();

                    cs.beginText();
                    cs.setFont(fontRegular, 8);
                    cs.newLineAtOffset(lx + 55, ly);
                    cs.showText(values[i] != null ? values[i] : "-");
                    cs.endText();
                }

                y -= 85;

                // ── Transactions section title ─────────────────────────────
                cs.setNonStrokingColor(0.10f, 0.25f, 0.55f);
                cs.beginText();
                cs.setFont(fontBold, 11);
                cs.newLineAtOffset(MARGIN, y);
                cs.showText("Historique des transactions (" + transactions.size() + " operations)");
                cs.endText();

                y -= 15;

                // ── Table header ──────────────────────────────────────────
                float[] colX     = { MARGIN, MARGIN + 75, MARGIN + 155, MARGIN + 270, MARGIN + 390 };
                String[] headers = { "Date", "Type", "Description", "Montant", "Statut" };

                cs.setNonStrokingColor(0.10f, 0.25f, 0.55f);
                cs.addRect(MARGIN, y - 14, PAGE_WIDTH - 2 * MARGIN, 18);
                cs.fill();

                cs.setNonStrokingColor(1f, 1f, 1f);
                for (int i = 0; i < headers.length; i++) {
                    cs.beginText();
                    cs.setFont(fontBold, 8);
                    cs.newLineAtOffset(colX[i] + 3, y - 8);
                    cs.showText(headers[i]);
                    cs.endText();
                }

                y -= 20;

                // ── Transaction rows ──────────────────────────────────────
                if (transactions.isEmpty()) {
                    cs.setNonStrokingColor(0.5f, 0.5f, 0.5f);
                    cs.beginText();
                    cs.setFont(fontRegular, 9);
                    cs.newLineAtOffset(MARGIN + 10, y - 10);
                    cs.showText("Aucune transaction enregistree.");
                    cs.endText();
                } else {
                    boolean alternate = false;
                    for (Transaction tx : transactions) {
                        if (y < MARGIN + 30) break; // avoid overflow for very large histories

                        if (alternate) {
                            cs.setNonStrokingColor(0.97f, 0.97f, 0.97f);
                            cs.addRect(MARGIN, y - 12, PAGE_WIDTH - 2 * MARGIN, 16);
                            cs.fill();
                        }
                        alternate = !alternate;

                        boolean isDebit = accountId.equals(tx.getFromAccountId());
                        String amtStr = (isDebit ? "- " : "+ ") +
                                formatAmount(tx.getAmount(), tx.getCurrency() != null ? tx.getCurrency() : "EUR");

                        String dateStr = tx.getCreatedAt() != null
                                ? tx.getCreatedAt().format(SHORT_DATE_FMT) : "-";
                        String typeStr = translateType(tx.getType(), isDebit);
                        String descStr = tx.getDescription() != null
                                ? truncate(tx.getDescription(), 18) : "-";
                        String statStr = translateStatus(tx.getStatus());

                        float textY = y - 8;

                        // Date
                        drawCell(cs, fontRegular, 8, colX[0] + 3, textY, dateStr, 0.2f, 0.2f, 0.2f);
                        // Type
                        drawCell(cs, fontRegular, 8, colX[1] + 3, textY, typeStr, 0.2f, 0.2f, 0.2f);
                        // Description
                        drawCell(cs, fontRegular, 8, colX[2] + 3, textY, descStr, 0.35f, 0.35f, 0.35f);
                        // Amount — coloured
                        float r = isDebit ? 0.75f : 0.13f;
                        float g = isDebit ? 0.18f : 0.55f;
                        float b = isDebit ? 0.18f : 0.27f;
                        drawCell(cs, fontBold, 8, colX[3] + 3, textY, amtStr, r, g, b);
                        // Status
                        drawCell(cs, fontRegular, 7, colX[4] + 3, textY, statStr, 0.35f, 0.35f, 0.35f);

                        y -= 16;
                    }
                }

                // ── Footer ────────────────────────────────────────────────
                cs.setNonStrokingColor(0.6f, 0.6f, 0.6f);
                cs.addRect(MARGIN, MARGIN - 5, PAGE_WIDTH - 2 * MARGIN, 0.5f);
                cs.fill();

                cs.beginText();
                cs.setFont(fontRegular, 7);
                cs.newLineAtOffset(MARGIN, MARGIN - 16);
                cs.showText("SolarisBank SA - Document confidentiel genere automatiquement - " +
                        "Ne pas divulguer");
                cs.endText();
            }

            doc.save(out);
            return out.toByteArray();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void drawCell(PDPageContentStream cs, PDType1Font font, float size,
                          float x, float y, String text, float r, float g, float b)
            throws IOException {
        cs.setNonStrokingColor(r, g, b);
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private String formatAmount(BigDecimal amount, String currency) {
        if (amount == null) return "-";
        // Use US locale to avoid NNBSP (U+202F) and NBSP (U+00A0) produced by Locale.FRANCE,
        // which are not encodable in Helvetica's WinAnsiEncoding.
        return String.format(Locale.US, "%.2f %s", amount, currency);
    }

    private String translateType(Transaction.Type type, boolean isDebit) {
        if (type == null) return "-";
        return switch (type) {
            case TRANSFER   -> isDebit ? "Virement emis" : "Virement recu";
            case DEPOSIT    -> "Depot";
            case WITHDRAWAL -> "Retrait";
        };
    }

    private String translateStatus(Transaction.Status status) {
        if (status == null) return "-";
        return switch (status) {
            case COMPLETED        -> "Complete";
            case PENDING          -> "En attente";
            case FAILED           -> "Echoue";
            case CANCELLED        -> "Annule";
            case DEBIT_CONFIRMED  -> "En cours";
        };
    }

    private String truncate(String s, int max) {
        // Use ASCII "..." — U+2026 (horizontal ellipsis) is not in WinAnsiEncoding
        return s.length() <= max ? s : s.substring(0, max - 1) + "...";
    }
}
