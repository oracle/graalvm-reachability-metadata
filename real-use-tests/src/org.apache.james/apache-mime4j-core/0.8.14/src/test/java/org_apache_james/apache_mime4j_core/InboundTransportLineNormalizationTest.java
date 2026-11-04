/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import org.apache.james.mime4j.io.EOLConvertingInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/// Normalizes line endings in inbound SMTP transport streams before MIME processing.
public class InboundTransportLineNormalizationTest {
    @Test
    @Timeout(60)
    public void normalizesTransportLineEndingsBeforeProcessing() throws Exception {
        byte[] receivedMessage = ("Received: from edge.example\r\n"
                + "X-Trace-Id: trace-42\n"
                + "\n"
                + "Delivery body\r\n").getBytes(StandardCharsets.US_ASCII);

        try (EOLConvertingInputStream normalizedMessage = new EOLConvertingInputStream(
                new ByteArrayInputStream(receivedMessage))) {
            assertArrayEquals(("Received: from edge.example\r\n"
                    + "X-Trace-Id: trace-42\r\n"
                    + "\r\n"
                    + "Delivery body\r\n").getBytes(StandardCharsets.US_ASCII),
                    normalizedMessage.readAllBytes());
        }
    }
}
