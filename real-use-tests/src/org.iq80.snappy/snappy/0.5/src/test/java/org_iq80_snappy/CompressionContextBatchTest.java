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
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Snappy's reusable compression-context subsystem for a batch of application records.
public class CompressionContextBatchTest {
    @Test
    @Timeout(60)
    public void compressesSeveralRecordsWithCallerOwnedBuffers() {
        Snappy.CompressionContext context = new Snappy.CompressionContext();
        byte[] firstRecord = record("order-1042", "packed", 18);
        byte[] secondRecord = record("order-1043", "awaiting-carrier", 27);

        byte[] restoredFirst = compressAndRestore(context, firstRecord, 7);
        byte[] restoredSecond = compressAndRestore(context, secondRecord, 11);

        assertArrayEquals(firstRecord, restoredFirst);
        assertArrayEquals(secondRecord, restoredSecond);
    }

    private static byte[] compressAndRestore(
            Snappy.CompressionContext context, byte[] record, int sourceOffset) {
        byte[] source = new byte[sourceOffset + record.length + 5];
        Arrays.fill(source, (byte) '#');
        System.arraycopy(record, 0, source, sourceOffset, record.length);

        int compressedOffset = 13;
        byte[] compressed = new byte[compressedOffset + Snappy.maxCompressedLength(record.length) + 9];
        int compressedLength = Snappy.compress(
                context, source, sourceOffset, record.length,
                compressed, compressedOffset, compressed.length - compressedOffset);

        int restoredOffset = 4;
        byte[] restored = new byte[restoredOffset + record.length + 6];
        Arrays.fill(restored, (byte) '!');
        int restoredLength = Snappy.uncompress(
                compressed, compressedOffset, compressedLength,
                restored, restoredOffset, record.length);

        assertEquals(record.length, restoredLength);
        return Arrays.copyOfRange(restored, restoredOffset, restoredOffset + restoredLength);
    }

    private static byte[] record(String orderId, String status, int itemCount) {
        String value = "order=" + orderId + "|status=" + status + "|items=" + itemCount
                + "|warehouse=denver|channel=online";
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
