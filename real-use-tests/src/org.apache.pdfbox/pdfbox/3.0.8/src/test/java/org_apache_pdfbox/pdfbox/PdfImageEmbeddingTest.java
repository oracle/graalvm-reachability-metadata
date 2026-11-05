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
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises PDFBox's image embedding subsystem to place a customer-uploaded image in a report.
public class PdfImageEmbeddingTest {
    @Test
    @Timeout(60)
    public void embedsAndReloadsCustomerImage() throws IOException {
        BufferedImage uploadedImage = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        uploadedImage.setRGB(0, 0, Color.RED.getRGB());
        uploadedImage.setRGB(1, 0, Color.GREEN.getRGB());
        uploadedImage.setRGB(0, 1, Color.BLUE.getRGB());
        uploadedImage.setRGB(1, 1, Color.WHITE.getRGB());

        byte[] reportPdf;
        try (PDDocument report = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            report.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(report, uploadedImage);
            try (PDPageContentStream content = new PDPageContentStream(report, page)) {
                content.drawImage(image, 72, 648, 144, 144);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            report.save(output);
            reportPdf = output.toByteArray();
        }

        try (PDDocument loadedReport = Loader.loadPDF(reportPdf)) {
            PDImageXObject embeddedImage = findEmbeddedImage(loadedReport.getPage(0));
            BufferedImage decodedImage = embeddedImage.getImage();

            assertEquals(2, embeddedImage.getWidth());
            assertEquals(2, embeddedImage.getHeight());
            assertEquals(Color.RED.getRGB(), decodedImage.getRGB(0, 0));
            assertEquals(Color.GREEN.getRGB(), decodedImage.getRGB(1, 0));
            assertEquals(Color.BLUE.getRGB(), decodedImage.getRGB(0, 1));
            assertEquals(Color.WHITE.getRGB(), decodedImage.getRGB(1, 1));
        }
    }

    private static PDImageXObject findEmbeddedImage(PDPage page) throws IOException {
        PDImageXObject embeddedImage = null;
        for (COSName name : page.getResources().getXObjectNames()) {
            if (page.getResources().isImageXObject(name)) {
                PDXObject xObject = page.getResources().getXObject(name);
                embeddedImage = assertInstanceOf(PDImageXObject.class, xObject);
                break;
            }
        }
        assertNotNull(embeddedImage, "The report should contain an embedded image");
        return embeddedImage;
    }
}
