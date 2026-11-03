/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.pdfbox;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
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

/// Exercises PDFBox's document merging subsystem to combine two report sections into one downloadable PDF.
public class PdfDocumentMergingTest {
    @Test
    @Timeout(60)
    public void mergesReportSectionsAndPreservesTheirText() throws IOException {
        byte[] summaryPdf = createReportSection("Quarterly summary", "Revenue: 25000.00");
        byte[] detailPdf = createReportSection("Order details", "Orders fulfilled: 42");
        byte[] mergedPdf;

        try (PDDocument summary = Loader.loadPDF(summaryPdf);
                PDDocument detail = Loader.loadPDF(detailPdf);
                PDDocument merged = new PDDocument()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.appendDocument(merged, summary);
            merger.appendDocument(merged, detail);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            merged.save(output);
            mergedPdf = output.toByteArray();
        }

        try (PDDocument merged = Loader.loadPDF(mergedPdf)) {
            assertEquals(2, merged.getNumberOfPages());

            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);
            String extractedText = textStripper.getText(merged);
            assertTrue(extractedText.contains("Quarterly summary"));
            assertTrue(extractedText.contains("Revenue: 25000.00"));
            assertTrue(extractedText.contains("Order details"));
            assertTrue(extractedText.contains("Orders fulfilled: 42"));
        }
    }

    private static byte[] createReportSection(String heading, String detail) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(heading);
                content.newLineAtOffset(0, -18);
                content.showText(detail);
                content.endText();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }
}
