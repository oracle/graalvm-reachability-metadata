/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.pdfbox;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises PDFBox's document authoring and text extraction subsystem to create an invoice and read it back.
public class InvoiceDocumentTextExtractionTest {
    @Test
    @Timeout(60)
    public void createsAnInvoicePdfAndExtractsItsText() throws IOException {
        byte[] invoicePdf;
        try (PDDocument invoice = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            invoice.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(invoice, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("Invoice INV-1042");
                content.newLineAtOffset(0, -18);
                content.showText("Total: 125.50");
                content.endText();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            invoice.save(output);
            invoicePdf = output.toByteArray();
        }

        try (PDDocument loadedInvoice = Loader.loadPDF(invoicePdf)) {
            assertEquals(1, loadedInvoice.getNumberOfPages());
            assertEquals(PDRectangle.LETTER.getWidth(), loadedInvoice.getPage(0).getMediaBox().getWidth());

            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);
            String extractedText = textStripper.getText(loadedInvoice);
            assertTrue(extractedText.contains("Invoice INV-1042"));
            assertTrue(extractedText.contains("Total: 125.50"));
        }
    }
}
