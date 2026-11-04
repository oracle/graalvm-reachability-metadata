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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Snappy's framed payload selection subsystem for an application storing encrypted snapshots.
public class FramedPayloadSelectionTest {
    @Test
    @Timeout(60)
    public void preservesAnIncompressibleEncryptedSnapshotWithoutExpandingItsPayload() throws IOException {
        byte[] snapshot = createEncryptedSnapshot();
        ByteArrayOutputStream storedSnapshot = new ByteArrayOutputStream();

        try (SnappyFramedOutputStream writer =
                     new SnappyFramedOutputStream(storedSnapshot, snapshot.length, 0.99)) {
            writer.write(snapshot);
        }

        byte[] framedSnapshot = storedSnapshot.toByteArray();
        assertEquals(snapshot.length + 18, framedSnapshot.length);

        ByteArrayOutputStream restoredSnapshot = new ByteArrayOutputStream(snapshot.length);
        try (SnappyFramedInputStream reader = new SnappyFramedInputStream(
                new ByteArrayInputStream(framedSnapshot))) {
            reader.transferTo(restoredSnapshot);
        }

        assertArrayEquals(snapshot, restoredSnapshot.toByteArray());
    }

    private static byte[] createEncryptedSnapshot() {
        byte[] snapshot = new byte[1024];
        long state = 0x9e3779b97f4a7c15L;
        for (int index = 0; index < snapshot.length; index++) {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            snapshot[index] = (byte) state;
        }
        return snapshot;
    }
}
