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

/// Exercises the checksum-free framed-stream subsystem used by an application benchmark.
public class BenchmarkFramedArchiveTest {
    @Test
    @Timeout(60)
    public void roundTripsBenchmarkArchiveWithoutChecksums() throws IOException {
        byte[] archive = createBenchmarkArchive();
        ByteArrayOutputStream compressedArchive = new ByteArrayOutputStream();

        try (SnappyFramedOutputStream writer =
                     SnappyFramedOutputStream.newChecksumFreeBenchmarkOutputStream(compressedArchive)) {
            writer.write(archive, 0, 23);
            writer.write(archive, 23, archive.length - 23);
        }

        byte[] compressedBytes = compressedArchive.toByteArray();
        assertTrue(compressedBytes.length < archive.length);

        ByteArrayOutputStream restoredArchive = new ByteArrayOutputStream(archive.length);
        try (SnappyFramedInputStream reader = new SnappyFramedInputStream(
                new ByteArrayInputStream(compressedBytes), false)) {
            byte[] buffer = new byte[193];
            int bytesRead;
            while ((bytesRead = reader.read(buffer)) != -1) {
                restoredArchive.write(buffer, 0, bytesRead);
            }
        }

        assertArrayEquals(archive, restoredArchive.toByteArray());
    }

    private static byte[] createBenchmarkArchive() {
        StringBuilder archive = new StringBuilder();
        for (int sample = 0; sample < 4096; sample++) {
            archive.append("sample=").append(sample)
                    .append("|service=inventory|operation=lookup|status=success")
                    .append("|region=denver|durationMicros=184\n");
        }
        return archive.toString().getBytes(StandardCharsets.UTF_8);
    }
}
