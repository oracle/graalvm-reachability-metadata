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
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises PDFBox's accessibility tagging subsystem to publish a structured report heading and paragraph.
public class PdfAccessibilityTaggingTest {
    @Test
    @Timeout(60)
    public void createsAndReloadsStructuredReportContent() throws IOException {
        byte[] reportPdf;
        try (PDDocument report = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            page.setStructParents(0);
            report.addPage(page);

            PDMarkInfo markInfo = new PDMarkInfo();
            markInfo.setMarked(true);
            report.getDocumentCatalog().setMarkInfo(markInfo);
            report.getDocumentCatalog().setLanguage("en-US");

            PDStructureTreeRoot structureRoot = new PDStructureTreeRoot();
            report.getDocumentCatalog().setStructureTreeRoot(structureRoot);
            PDStructureElement documentElement = new PDStructureElement(
                    StandardStructureTypes.DOCUMENT, structureRoot);
            structureRoot.appendKid(documentElement);

            PDStructureElement heading = new PDStructureElement(StandardStructureTypes.H1, documentElement);
            heading.setTitle("Quarterly report");
            heading.setPage(page);
            documentElement.appendKid(heading);

            PDStructureElement paragraph = new PDStructureElement(StandardStructureTypes.P, documentElement);
            paragraph.setActualText("Revenue increased by 12 percent.");
            paragraph.setPage(page);
            documentElement.appendKid(paragraph);

            try (PDPageContentStream content = new PDPageContentStream(report, page)) {
                content.beginMarkedContent(COSName.getPDFName("H1"), 0);
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                content.newLineAtOffset(72, 720);
                content.showText("Quarterly report");
                content.endText();
                content.endMarkedContent();

                content.beginMarkedContent(COSName.getPDFName("P"), 1);
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 690);
                content.showText("Revenue increased by 12 percent.");
                content.endText();
                content.endMarkedContent();
            }

            PDMarkedContentReference headingReference = new PDMarkedContentReference();
            headingReference.setPage(page);
            headingReference.setMCID(0);
            heading.appendKid(headingReference);

            PDMarkedContentReference paragraphReference = new PDMarkedContentReference();
            paragraphReference.setPage(page);
            paragraphReference.setMCID(1);
            paragraph.appendKid(paragraphReference);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            report.save(output);
            reportPdf = output.toByteArray();
        }

        try (PDDocument loadedReport = Loader.loadPDF(reportPdf)) {
            PDMarkInfo markInfo = loadedReport.getDocumentCatalog().getMarkInfo();
            assertNotNull(markInfo);
            assertTrue(markInfo.isMarked());
            assertEquals("en-US", loadedReport.getDocumentCatalog().getLanguage());

            PDStructureTreeRoot structureRoot = loadedReport.getDocumentCatalog().getStructureTreeRoot();
            assertNotNull(structureRoot);
            List<Object> rootKids = structureRoot.getKids();
            PDStructureElement documentElement = assertInstanceOf(PDStructureElement.class, rootKids.get(0));
            assertEquals(StandardStructureTypes.DOCUMENT, documentElement.getStructureType());

            List<Object> documentKids = documentElement.getKids();
            PDStructureElement heading = assertInstanceOf(PDStructureElement.class, documentKids.get(0));
            PDStructureElement paragraph = assertInstanceOf(PDStructureElement.class, documentKids.get(1));
            assertEquals(StandardStructureTypes.H1, heading.getStructureType());
            assertEquals("Quarterly report", heading.getTitle());
            assertEquals(StandardStructureTypes.P, paragraph.getStructureType());
            assertEquals("Revenue increased by 12 percent.", paragraph.getActualText());
            assertEquals(2, documentKids.size());
            assertEquals(1, loadedReport.getNumberOfPages());
        }
    }
}
