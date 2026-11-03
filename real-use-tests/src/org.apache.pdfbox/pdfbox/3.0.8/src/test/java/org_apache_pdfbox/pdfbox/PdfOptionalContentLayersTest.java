/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.pdfbox;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises PDFBox's optional-content subsystem to publish a report with independently switchable layers.
public class PdfOptionalContentLayersTest {
    @Test
    @Timeout(60)
    public void savesAndReloadsReportLayers() throws IOException {
        byte[] reportPdf;
        try (PDDocument report = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            report.addPage(page);

            PDOptionalContentGroup financialLayer = new PDOptionalContentGroup("Financial results");
            PDOptionalContentGroup notesLayer = new PDOptionalContentGroup("Reviewer notes");
            PDOptionalContentProperties layers = new PDOptionalContentProperties();
            layers.addGroup(financialLayer);
            layers.addGroup(notesLayer);
            layers.setBaseState(PDOptionalContentProperties.BaseState.ON);
            layers.setGroupEnabled(notesLayer, false);
            report.getDocumentCatalog().setOCProperties(layers);

            try (PDPageContentStream content = new PDPageContentStream(report, page)) {
                writeLayer(content, financialLayer, "Revenue: 25000.00", 720);
                writeLayer(content, notesLayer, "Reviewer note: verify regional totals", 690);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            report.save(output);
            reportPdf = output.toByteArray();
        }

        try (PDDocument loadedReport = Loader.loadPDF(reportPdf)) {
            PDOptionalContentProperties layers = loadedReport.getDocumentCatalog().getOCProperties();
            assertNotNull(layers);
            assertArrayEquals(
                    new String[] {"Financial results", "Reviewer notes"}, layers.getGroupNames());
            assertTrue(layers.hasGroup("Financial results"));
            assertTrue(layers.isGroupEnabled("Financial results"));
            assertFalse(layers.isGroupEnabled("Reviewer notes"));

            PDOptionalContentGroup reviewerNotes = layers.getGroup("Reviewer notes");
            assertNotNull(reviewerNotes);
            assertEquals("Reviewer notes", reviewerNotes.getName());
            assertTrue(layers.setGroupEnabled(reviewerNotes, true));
            assertTrue(layers.isGroupEnabled("Reviewer notes"));
            assertEquals(1, loadedReport.getNumberOfPages());
        }
    }

    private static void writeLayer(
            PDPageContentStream content, PDOptionalContentGroup layer, String text, float y)
            throws IOException {
        content.beginMarkedContent(COSName.OC, layer);
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(72, y);
        content.showText(text);
        content.endText();
        content.endMarkedContent();
    }
}
