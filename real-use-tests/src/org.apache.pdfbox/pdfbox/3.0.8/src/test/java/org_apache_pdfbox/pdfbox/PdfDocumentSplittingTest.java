/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.pdfbox;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.Splitter;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises PDFBox's document splitting subsystem to deliver individual pages from a multi-page report.
public class PdfDocumentSplittingTest {
    @Test
    @Timeout(60)
    public void splitsReportIntoDownloadablePages() throws IOException {
        byte[] reportPdf = createReport();
        List<PDDocument> pages;
        try (PDDocument report = Loader.loadPDF(reportPdf)) {
            pages = new Splitter().split(report);
        }

        try {
            assertEquals(3, pages.size());
            assertPageText(pages.get(0), "Summary page");
            assertPageText(pages.get(1), "Transactions page");
            assertPageText(pages.get(2), "Appendix page");
        } finally {
            for (PDDocument page : pages) {
                page.close();
            }
        }
    }

    private static byte[] createReport() throws IOException {
        try (PDDocument report = new PDDocument()) {
            addPage(report, "Summary page");
            addPage(report, "Transactions page");
            addPage(report, "Appendix page");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            report.save(output);
            return output.toByteArray();
        }
    }

    private static void addPage(PDDocument report, String text) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        report.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(report, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(72, 720);
            content.showText(text);
            content.endText();
        }
    }

    private static void assertPageText(PDDocument page, String expectedText) throws IOException {
        assertEquals(1, page.getNumberOfPages());
        String text = new PDFTextStripper().getText(page);
        assertTrue(text.contains(expectedText));
    }
}
