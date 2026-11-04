/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_iq80_snappy;

import org.iq80.snappy.SnappyFramedInputStream;
import org.iq80.snappy.SnappyFramedOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Snappy's framed streaming subsystem to compress and restore an application log archive.
public class LogArchiveFramingTest {
    @Test
    @Timeout(60)
    public void roundTripsAChunkedLogArchiveThroughFramedStreams() throws IOException {
        byte[] logArchive = createLogArchive();
        ByteArrayOutputStream compressedArchive = new ByteArrayOutputStream();

        try (SnappyFramedOutputStream writer = new SnappyFramedOutputStream(compressedArchive, 1024, 0.9)) {
            writer.write(logArchive, 0, 17);
            writer.write(logArchive, 17, logArchive.length - 17);
        }

        byte[] compressedBytes = compressedArchive.toByteArray();
        assertTrue(compressedBytes.length < logArchive.length);

        ByteArrayOutputStream restoredArchive = new ByteArrayOutputStream(logArchive.length);
        try (SnappyFramedInputStream reader = new SnappyFramedInputStream(
                new ByteArrayInputStream(compressedBytes))) {
            byte[] buffer = new byte[257];
            int bytesRead;
            while ((bytesRead = reader.read(buffer)) != -1) {
                restoredArchive.write(buffer, 0, bytesRead);
            }
        }

        assertArrayEquals(logArchive, restoredArchive.toByteArray());
    }

    private static byte[] createLogArchive() {
        StringBuilder log = new StringBuilder();
        for (int request = 0; request < 4096; request++) {
            log.append("2025-01-15T12:00:00Z INFO request=")
                    .append(request)
                    .append(" route=/inventory/items status=200 durationMs=18\n");
        }
        return log.toString().getBytes(StandardCharsets.UTF_8);
    }
}
