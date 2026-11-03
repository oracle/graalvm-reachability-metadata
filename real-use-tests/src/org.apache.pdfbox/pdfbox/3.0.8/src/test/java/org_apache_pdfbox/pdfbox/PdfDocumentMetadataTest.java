/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.pdfbox;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises PDFBox's document metadata subsystem to publish and read back report catalog information.
public class PdfDocumentMetadataTest {
    @Test
    @Timeout(60)
    public void savesAndReloadsReportMetadata() throws IOException {
        byte[] reportPdf;
        Calendar creationDate = fixedDate();
        try (PDDocument report = new PDDocument()) {
            PDDocumentInformation information = report.getDocumentInformation();
            information.setTitle("Quarterly revenue report");
            information.setAuthor("Finance Operations");
            information.setSubject("Revenue by region");
            information.setKeywords("quarterly, revenue, finance");
            information.setCreator("Finance reporting service");
            information.setProducer("Finance reporting service");
            information.setCreationDate(creationDate);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            report.save(output);
            reportPdf = output.toByteArray();
        }

        try (PDDocument loadedReport = Loader.loadPDF(reportPdf)) {
            PDDocumentInformation information = loadedReport.getDocumentInformation();
            assertEquals("Quarterly revenue report", information.getTitle());
            assertEquals("Finance Operations", information.getAuthor());
            assertEquals("Revenue by region", information.getSubject());
            assertEquals("quarterly, revenue, finance", information.getKeywords());
            assertEquals("Finance reporting service", information.getCreator());
            assertEquals("Finance reporting service", information.getProducer());
            assertEquals(creationDate.getTime(), information.getCreationDate().getTime());
        }
    }

    private static Calendar fixedDate() {
        GregorianCalendar date = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        date.clear();
        date.set(2024, Calendar.JANUARY, 15, 10, 30, 0);
        return date;
    }
}
