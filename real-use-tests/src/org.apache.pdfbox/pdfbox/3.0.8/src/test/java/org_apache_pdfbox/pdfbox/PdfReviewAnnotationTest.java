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
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/// Exercises PDFBox's review annotation subsystem to attach and reload a comment on a report page.
public class PdfReviewAnnotationTest {
    @Test
    @Timeout(60)
    @SuppressWarnings("checkstyle:annotationAccess")
    public void savesAndReloadsReportReviewComment() throws IOException {
        byte[] reportPdf;
        try (PDDocument report = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            report.addPage(page);

            PDAnnotationText reviewComment = new PDAnnotationText();
            reviewComment.setContents("Verify the regional totals before publishing.");
            reviewComment.setName("finance-review");
            reviewComment.setRectangle(new PDRectangle(72, 648, 24, 24));
            page.getAnnotations().add(reviewComment);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            report.save(output);
            reportPdf = output.toByteArray();
        }

        try (PDDocument loadedReport = Loader.loadPDF(reportPdf)) {
            List<?> annotations = loadedReport.getPage(0).getAnnotations();
            PDAnnotationText loadedComment = assertInstanceOf(PDAnnotationText.class, annotations.get(0));
            assertEquals(1, annotations.size());
            assertEquals("Verify the regional totals before publishing.", loadedComment.getContents());
            assertEquals("finance-review", loadedComment.getName());
            assertEquals(72, loadedComment.getRectangle().getLowerLeftX());
            assertEquals(648, loadedComment.getRectangle().getLowerLeftY());
        }
    }
}
