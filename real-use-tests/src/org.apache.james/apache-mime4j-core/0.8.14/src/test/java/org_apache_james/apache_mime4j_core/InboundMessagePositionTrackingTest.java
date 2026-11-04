/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import org.apache.james.mime4j.io.PositionInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tracks byte offsets while an inbound mail gateway scans a MIME message.
public class InboundMessagePositionTrackingTest {
    @Test
    @Timeout(60)
    public void recordsBodyOffsetWhileGatewaySkipsAlreadyInspectedBytes() throws Exception {
        String headers = "From: alerts@example.test\r\n"
                + "Subject: nightly report\r\n"
                + "\r\n";
        byte[] body = "Report is ready for download.\r\n".getBytes(StandardCharsets.US_ASCII);
        byte[] message = (headers + new String(body, StandardCharsets.US_ASCII))
                .getBytes(StandardCharsets.US_ASCII);

        try (PositionInputStream trackedMessage = new PositionInputStream(
                new ByteArrayInputStream(message))) {
            byte[] headerBytes = trackedMessage.readNBytes(headers.length());
            assertArrayEquals(headers.getBytes(StandardCharsets.US_ASCII), headerBytes);
            assertEquals(headers.length(), trackedMessage.getPosition());

            long inspectedBytes = trackedMessage.skip(7);
            assertEquals(7, inspectedBytes);
            assertEquals(headers.length() + inspectedBytes, trackedMessage.getPosition());

            byte[] remainingBody = trackedMessage.readAllBytes();
            assertArrayEquals("is ready for download.\r\n".getBytes(StandardCharsets.US_ASCII), remainingBody);
            assertEquals(message.length, trackedMessage.getPosition());
        }
    }
}
