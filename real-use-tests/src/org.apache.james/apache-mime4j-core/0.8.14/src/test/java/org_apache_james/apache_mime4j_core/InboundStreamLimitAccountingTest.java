/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import org.apache.james.mime4j.io.InputStreams;
import org.apache.james.mime4j.io.LineNumberInputStream;
import org.apache.james.mime4j.io.LimitedInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tracks line numbers and bounds raw MIME stream reads for an inbound mail gateway.
public class InboundStreamLimitAccountingTest {
    @Test
    @Timeout(60)
    public void limitsPreviewWithoutLosingLineAccountingForTheRemainingMessage() throws Exception {
        String firstLine = "From: alerts@example.test\r\n";
        String remainingMessage = "Subject: nightly report\r\n\r\nThe report is ready.\r\n";
        String message = firstLine + remainingMessage;

        LineNumberInputStream source = new LineNumberInputStream(InputStreams.createAscii(message));
        LimitedInputStream preview = new LimitedInputStream(source,
                firstLine.getBytes(StandardCharsets.US_ASCII).length);

        byte[] previewBytes = new byte[firstLine.getBytes(StandardCharsets.US_ASCII).length];
        assertEquals(previewBytes.length, preview.read(previewBytes));
        assertArrayEquals(firstLine.getBytes(StandardCharsets.US_ASCII), previewBytes);
        assertEquals(2, source.getLineNumber());
        assertEquals(0, preview.available());
        assertThrows(IOException.class, preview::read);
        assertEquals(firstLine.getBytes(StandardCharsets.US_ASCII).length, preview.getPosition());

        assertArrayEquals(remainingMessage.getBytes(StandardCharsets.US_ASCII), source.readAllBytes());
        assertEquals(5, source.getLineNumber());
    }
}
