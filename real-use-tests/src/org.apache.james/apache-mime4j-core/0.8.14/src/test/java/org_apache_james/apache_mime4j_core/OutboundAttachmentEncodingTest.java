/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import org.apache.james.mime4j.codec.Base64InputStream;
import org.apache.james.mime4j.codec.Base64OutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Encodes binary attachments for outbound MIME messages with transfer-codec streams.
public class OutboundAttachmentEncodingTest {
    @Test
    @Timeout(60)
    public void encodesAttachmentForEmailDelivery() throws Exception {
        byte[] attachment = "invoice-id=INV-42\namount=EUR 19.95\n".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream wireBody = new ByteArrayOutputStream();

        try (Base64OutputStream encoder = new Base64OutputStream(wireBody, 76)) {
            encoder.write(attachment);
        }

        assertEquals("aW52b2ljZS1pZD1JTlYtNDIKYW1vdW50PUVVUiAxOS45NQo=\r\n",
                wireBody.toString(StandardCharsets.US_ASCII));

        try (Base64InputStream decoder = new Base64InputStream(
                new ByteArrayInputStream(wireBody.toByteArray()))) {
            assertArrayEquals(attachment, decoder.readAllBytes());
        }
    }
}
