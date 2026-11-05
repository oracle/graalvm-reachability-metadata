/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import org.apache.james.mime4j.io.BufferedLineReaderInputStream;
import org.apache.james.mime4j.io.MimeBoundaryInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Extracts individual parts from a multipart message for a mail gateway's attachment pipeline.
public class MultipartBoundaryExtractionTest {
    @Test
    @Timeout(60)
    public void readsMultipartPartsWithoutConsumingTheNextPart() throws Exception {
        String multipartBody = "text part for order-42\r\n"
                + "--order-42\r\n"
                + "binary report for order-42\r\n"
                + "--order-42--\r\n";
        BufferedLineReaderInputStream input = new BufferedLineReaderInputStream(
                new ByteArrayInputStream(multipartBody.getBytes(StandardCharsets.US_ASCII)), 128);

        MimeBoundaryInputStream textPart = new MimeBoundaryInputStream(input, "order-42");
        assertEquals("text part for order-42", readAscii(textPart));
        assertFalse(textPart.isLastPart());

        MimeBoundaryInputStream reportPart = new MimeBoundaryInputStream(input, "order-42");
        assertEquals("binary report for order-42", readAscii(reportPart));
        assertTrue(reportPart.isLastPart());
    }

    private static String readAscii(MimeBoundaryInputStream part) throws Exception {
        return new String(part.readAllBytes(), StandardCharsets.US_ASCII);
    }
}
