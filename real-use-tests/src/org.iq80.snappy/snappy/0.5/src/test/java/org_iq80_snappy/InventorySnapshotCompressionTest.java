/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_iq80_snappy;

import org.iq80.snappy.Snappy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Snappy's raw block compression subsystem for an inventory snapshot payload.
public class InventorySnapshotCompressionTest {
    @Test
    @Timeout(60)
    public void compressesAndRestoresAnInventorySnapshot() {
        byte[] snapshot = createInventorySnapshot();

        byte[] compressedSnapshot = Snappy.compress(snapshot);

        assertTrue(compressedSnapshot.length < snapshot.length);
        assertEquals(snapshot.length, Snappy.getUncompressedLength(compressedSnapshot, 0));

        byte[] restoredSnapshot = Snappy.uncompress(compressedSnapshot, 0, compressedSnapshot.length);

        assertArrayEquals(snapshot, restoredSnapshot);
    }

    private static byte[] createInventorySnapshot() {
        StringBuilder snapshot = new StringBuilder();
        for (int product = 0; product < 2048; product++) {
            snapshot.append("sku=").append(product)
                    .append("|warehouse=denver|category=hardware|quantity=42|status=available\n");
        }
        return snapshot.toString().getBytes(StandardCharsets.UTF_8);
    }
}
