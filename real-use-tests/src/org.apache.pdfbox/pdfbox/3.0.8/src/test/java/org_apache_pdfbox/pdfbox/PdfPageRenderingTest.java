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
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises PDFBox's page rendering subsystem to rasterize a generated report page for a preview.
public class PdfPageRenderingTest {
    @Test
    @Timeout(60)
    public void rendersReportPageWithExpectedPreviewPixels() throws IOException {
        byte[] reportPdf;
        Color reportColor = new Color(20, 100, 180);
        try (PDDocument report = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            report.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(report, page)) {
                content.setNonStrokingColor(reportColor);
                content.addRect(72, 600, 200, 100);
                content.fill();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            report.save(output);
            reportPdf = output.toByteArray();
        }

        try (PDDocument loadedReport = Loader.loadPDF(reportPdf)) {
            PDFRenderer renderer = new PDFRenderer(loadedReport);
            BufferedImage preview = renderer.renderImageWithDPI(0, 72, ImageType.RGB);

            assertEquals(612, preview.getWidth());
            assertEquals(792, preview.getHeight());
            Color renderedReportColor = new Color(preview.getRGB(100, 142));
            assertEquals(reportColor.getRed(), renderedReportColor.getRed());
            assertEquals(reportColor.getGreen(), renderedReportColor.getGreen());
            assertEquals(reportColor.getBlue(), renderedReportColor.getBlue());
            Color pageBackground = new Color(preview.getRGB(20, 20));
            assertTrue(pageBackground.getRed() > 240);
            assertTrue(pageBackground.getGreen() > 240);
            assertTrue(pageBackground.getBlue() > 240);
        }
    }
}
