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
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises PDFBox's password-protection subsystem to publish a confidential report with restricted access.
public class PdfPasswordProtectionTest {
    private static final String OWNER_PASSWORD = "report-owner";
    private static final String USER_PASSWORD = "report-reader";

    @Test
    @Timeout(60)
    public void protectsReportAndAppliesReaderPermissions() throws IOException {
        byte[] protectedReport;
        try (PDDocument report = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            report.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(report, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("Confidential audit report");
                content.endText();
            }

            AccessPermission permissions = new AccessPermission();
            permissions.setCanExtractContent(false);
            permissions.setCanPrint(false);
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy(OWNER_PASSWORD, USER_PASSWORD, permissions);
            policy.setEncryptionKeyLength(256);
            policy.setPreferAES(true);
            report.protect(policy);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            report.save(output);
            protectedReport = output.toByteArray();
        }

        try (PDDocument readerReport = Loader.loadPDF(protectedReport, USER_PASSWORD)) {
            assertTrue(readerReport.isEncrypted());
            assertEquals(1, readerReport.getNumberOfPages());
            assertFalse(readerReport.getCurrentAccessPermission().canExtractContent());
            assertFalse(readerReport.getCurrentAccessPermission().canPrint());
        }

        try (PDDocument ownerReport = Loader.loadPDF(protectedReport, OWNER_PASSWORD)) {
            assertTrue(ownerReport.getCurrentAccessPermission().isOwnerPermission());
            PDFTextStripper textStripper = new PDFTextStripper();
            assertTrue(textStripper.getText(ownerReport).contains("Confidential audit report"));
        }
    }
}
