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

import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises framed-stream checksum validation for an application archive received from storage.
public class FramedArchiveIntegrityTest {
    @Test
    @Timeout(60)
    public void rejectsAnArchiveWithATamperedIntegrityCheck() throws IOException {
        byte[] archive = createArchive();
        byte[] storedArchive = writeArchive(archive);
        storedArchive[14] ^= 1;

        assertThrows(IOException.class, () -> readArchive(storedArchive));
    }

    private static byte[] writeArchive(byte[] archive) throws IOException {
        ByteArrayOutputStream storedArchive = new ByteArrayOutputStream();
        try (SnappyFramedOutputStream writer = new SnappyFramedOutputStream(storedArchive)) {
            writer.write(archive);
        }
        return storedArchive.toByteArray();
    }

    private static void readArchive(byte[] storedArchive) throws IOException {
        try (SnappyFramedInputStream reader = new SnappyFramedInputStream(
                new ByteArrayInputStream(storedArchive))) {
            byte[] buffer = new byte[512];
            while (reader.read(buffer) != -1) {
                // Consume the archive so that the framed stream validates its checksum.
            }
        }
    }

    private static byte[] createArchive() {
        StringBuilder archive = new StringBuilder();
        for (int record = 0; record < 2048; record++) {
            archive.append("job=").append(record)
                    .append("|queue=fulfillment|state=ready|region=denver\n");
        }
        return archive.toString().getBytes(StandardCharsets.UTF_8);
    }
}
