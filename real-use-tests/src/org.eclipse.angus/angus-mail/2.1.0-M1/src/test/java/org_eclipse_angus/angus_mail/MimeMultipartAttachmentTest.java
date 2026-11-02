/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_mail;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import jakarta.activation.DataHandler;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * MIME multipart handling: an application composes a message with an attachment, serializes it,
 * and reads the parts back through Angus Mail's MIME handlers.
 */
class MimeMultipartAttachmentTest {

    private static final byte[] ATTACHMENT = "name,value\nalpha,1\nbeta,2\n"
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void composesAndReadsMultipartAttachment() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage outbound = new MimeMessage(session);
        outbound.setSubject("Build artifacts", StandardCharsets.UTF_8.name());

        MimeMultipart multipart = new MimeMultipart("mixed");
        MimeBodyPart summary = new MimeBodyPart();
        summary.setText("The build produced one report.", StandardCharsets.UTF_8.name());
        multipart.addBodyPart(summary);

        MimeBodyPart report = new MimeBodyPart();
        report.setDataHandler(new DataHandler(
                new ByteArrayDataSource(ATTACHMENT, "text/csv; charset=UTF-8")));
        report.setFileName("report.csv");
        multipart.addBodyPart(report);
        outbound.setContent(multipart);
        outbound.saveChanges();

        ByteArrayOutputStream wireMessage = new ByteArrayOutputStream();
        outbound.writeTo(wireMessage);
        MimeMessage received = new MimeMessage(session,
                new ByteArrayInputStream(wireMessage.toByteArray()));

        assertEquals("Build artifacts", received.getSubject());
        Multipart receivedParts = assertInstanceOf(Multipart.class, received.getContent());
        assertEquals(2, receivedParts.getCount());
        BodyPart receivedSummary = receivedParts.getBodyPart(0);
        assertEquals("The build produced one report.", receivedSummary.getContent().toString().strip());

        BodyPart receivedReport = receivedParts.getBodyPart(1);
        assertEquals("report.csv", receivedReport.getFileName());
        try (InputStream input = receivedReport.getInputStream()) {
            assertArrayEquals(ATTACHMENT, input.readAllBytes());
        }
    }
}
