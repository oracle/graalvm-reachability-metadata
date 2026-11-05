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

/// Exercises Snappy's framed-stream flush subsystem for an application log shipper.
public class FramedBatchFlushTest {
    @Test
    @Timeout(60)
    public void publishesEachLogBatchWhenTheFramedStreamIsFlushed() throws IOException {
        byte[] firstBatch = createBatch("morning", 300);
        byte[] secondBatch = createBatch("evening", 350);
        byte[] expected = concatenate(firstBatch, secondBatch);
        ByteArrayOutputStream storedLog = new ByteArrayOutputStream();

        try (SnappyFramedOutputStream writer = new SnappyFramedOutputStream(storedLog)) {
            int headerSize = storedLog.size();
            writer.write(firstBatch);
            assertTrue(storedLog.size() == headerSize);

            writer.flush();
            int firstBatchEnd = storedLog.size();
            assertTrue(firstBatchEnd > headerSize);

            writer.write(secondBatch);
            writer.close();
            assertTrue(storedLog.size() > firstBatchEnd);
        }

        ByteArrayOutputStream restoredLog = new ByteArrayOutputStream(expected.length);
        try (SnappyFramedInputStream reader = new SnappyFramedInputStream(
                new ByteArrayInputStream(storedLog.toByteArray()))) {
            reader.transferTo(restoredLog);
        }

        assertArrayEquals(expected, restoredLog.toByteArray());
    }

    private static byte[] createBatch(String shift, int recordCount) {
        StringBuilder batch = new StringBuilder();
        for (int record = 0; record < recordCount; record++) {
            batch.append("batch=").append(shift)
                    .append("|record=").append(record)
                    .append("|service=inventory|operation=ship|status=accepted\n");
        }
        return batch.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] combined = new byte[first.length + second.length];
        System.arraycopy(first, 0, combined, 0, first.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }
}
