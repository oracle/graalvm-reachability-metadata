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
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises PDFBox's document navigation subsystem to build a bookmarked report for readers.
public class PdfBookmarkNavigationTest {
    @Test
    @Timeout(60)
    public void savesAndReloadsReportBookmarks() throws IOException {
        byte[] reportPdf;
        try (PDDocument report = new PDDocument()) {
            PDPage summaryPage = new PDPage();
            PDPage transactionsPage = new PDPage();
            PDPage appendixPage = new PDPage();
            report.addPage(summaryPage);
            report.addPage(transactionsPage);
            report.addPage(appendixPage);

            PDDocumentOutline outline = new PDDocumentOutline();
            PDOutlineItem summary = bookmark("Executive summary", summaryPage);
            outline.addLast(summary);

            PDOutlineItem transactions = bookmark("Transactions", transactionsPage);
            transactions.addLast(bookmark("January", transactionsPage));
            transactions.addLast(bookmark("February", transactionsPage));
            outline.addLast(transactions);

            outline.addLast(bookmark("Appendix", appendixPage));
            outline.openNode();
            transactions.openNode();
            report.getDocumentCatalog().setDocumentOutline(outline);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            report.save(output);
            reportPdf = output.toByteArray();
        }

        try (PDDocument loadedReport = Loader.loadPDF(reportPdf)) {
            PDDocumentOutline outline = loadedReport.getDocumentCatalog().getDocumentOutline();
            assertNotNull(outline);

            PDOutlineItem summary = outline.getFirstChild();
            assertEquals("Executive summary", summary.getTitle());
            assertEquals(0, loadedReport.getPages().indexOf(summary.findDestinationPage(loadedReport)));

            PDOutlineItem transactions = summary.getNextSibling();
            assertEquals("Transactions", transactions.getTitle());
            assertEquals(1, loadedReport.getPages().indexOf(transactions.findDestinationPage(loadedReport)));
            assertEquals(2, countChildren(transactions));
            assertEquals("January", transactions.getFirstChild().getTitle());
            assertEquals("February", transactions.getLastChild().getTitle());

            PDOutlineItem appendix = transactions.getNextSibling();
            assertEquals("Appendix", appendix.getTitle());
            assertEquals(2, loadedReport.getPages().indexOf(appendix.findDestinationPage(loadedReport)));
        }
    }

    private static PDOutlineItem bookmark(String title, PDPage page) {
        PDOutlineItem bookmark = new PDOutlineItem();
        bookmark.setTitle(title);
        bookmark.setDestination(page);
        return bookmark;
    }

    private static int countChildren(PDOutlineItem item) {
        int count = 0;
        for (PDOutlineItem ignored : item.children()) {
            count++;
        }
        return count;
    }
}
