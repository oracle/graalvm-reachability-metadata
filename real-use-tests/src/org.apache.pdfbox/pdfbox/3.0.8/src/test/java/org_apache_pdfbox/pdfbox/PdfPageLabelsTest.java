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
import org.apache.pdfbox.pdmodel.common.PDPageLabelRange;
import org.apache.pdfbox.pdmodel.common.PDPageLabels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises PDFBox's page labeling subsystem to give a multi-section report readable page labels.
public class PdfPageLabelsTest {
    @Test
    @Timeout(60)
    public void savesAndReloadsSectionPageLabels() throws IOException {
        byte[] reportPdf;
        try (PDDocument report = new PDDocument()) {
            report.addPage(new PDPage());
            report.addPage(new PDPage());
            report.addPage(new PDPage());
            report.addPage(new PDPage());

            PDPageLabels pageLabels = new PDPageLabels(report);

            PDPageLabelRange preface = new PDPageLabelRange();
            preface.setStyle(PDPageLabelRange.STYLE_ROMAN_UPPER);
            preface.setPrefix("Preface ");
            pageLabels.setLabelItem(0, preface);

            PDPageLabelRange body = new PDPageLabelRange();
            body.setStyle(PDPageLabelRange.STYLE_DECIMAL);
            body.setPrefix("Section ");
            body.setStart(1);
            pageLabels.setLabelItem(2, body);

            PDPageLabelRange appendix = new PDPageLabelRange();
            appendix.setStyle(PDPageLabelRange.STYLE_LETTERS_UPPER);
            appendix.setPrefix("Appendix ");
            pageLabels.setLabelItem(3, appendix);
            report.getDocumentCatalog().setPageLabels(pageLabels);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            report.save(output);
            reportPdf = output.toByteArray();
        }

        try (PDDocument loadedReport = Loader.loadPDF(reportPdf)) {
            PDPageLabels loadedLabels = loadedReport.getDocumentCatalog().getPageLabels();
            assertEquals(3, loadedLabels.getPageRangeCount());
            assertArrayEquals(
                    new String[] {"Preface I", "Preface II", "Section 1", "Appendix A"},
                    loadedLabels.getLabelsByPageIndices());

            Map<String, Integer> pageIndicesByLabel = loadedLabels.getPageIndicesByLabels();
            assertEquals(0, pageIndicesByLabel.get("Preface I"));
            assertEquals(1, pageIndicesByLabel.get("Preface II"));
            assertEquals(2, pageIndicesByLabel.get("Section 1"));
            assertEquals(3, pageIndicesByLabel.get("Appendix A"));
            assertTrue(pageIndicesByLabel.keySet().containsAll(
                    java.util.List.of("Preface I", "Preface II", "Section 1", "Appendix A")));
        }
    }
}
