/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.pdfbox;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises PDFBox's embedded attachment subsystem to deliver a CSV export with a customer report.
public class PdfEmbeddedAttachmentTest {
    @Test
    @Timeout(60)
    public void embedsAndReloadsReportExport() throws IOException {
        byte[] export = "order_id,total\n1001,125.50\n1002,89.00\n".getBytes(StandardCharsets.UTF_8);
        byte[] reportPdf;

        try (PDDocument report = new PDDocument()) {
            PDComplexFileSpecification attachment = new PDComplexFileSpecification();
            attachment.setFile("orders.csv");
            attachment.setFileDescription("Order totals for the report");

            PDEmbeddedFile embeddedFile =
                    new PDEmbeddedFile(report, new ByteArrayInputStream(export));
            embeddedFile.setSubtype("text/csv");
            embeddedFile.setSize(export.length);
            attachment.setEmbeddedFile(embeddedFile);

            PDEmbeddedFilesNameTreeNode embeddedFiles = new PDEmbeddedFilesNameTreeNode();
            embeddedFiles.setNames(Map.of("orders.csv", attachment));
            PDDocumentNameDictionary names = new PDDocumentNameDictionary(report.getDocumentCatalog());
            names.setEmbeddedFiles(embeddedFiles);
            report.getDocumentCatalog().setNames(names);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            report.save(output);
            reportPdf = output.toByteArray();
        }

        try (PDDocument loadedReport = Loader.loadPDF(reportPdf)) {
            PDDocumentNameDictionary names = loadedReport.getDocumentCatalog().getNames();
            assertNotNull(names);
            PDEmbeddedFilesNameTreeNode embeddedFiles = names.getEmbeddedFiles();
            assertNotNull(embeddedFiles);
            PDComplexFileSpecification attachment = embeddedFiles.getValue("orders.csv");
            assertNotNull(attachment);
            assertEquals("orders.csv", attachment.getFilename());
            assertEquals("Order totals for the report", attachment.getFileDescription());

            PDEmbeddedFile embeddedFile = attachment.getEmbeddedFile();
            assertNotNull(embeddedFile);
            assertEquals("text/csv", embeddedFile.getSubtype());
            assertEquals(export.length, embeddedFile.getSize());
            assertArrayEquals(export, embeddedFile.createInputStream().readAllBytes());
        }
    }
}
