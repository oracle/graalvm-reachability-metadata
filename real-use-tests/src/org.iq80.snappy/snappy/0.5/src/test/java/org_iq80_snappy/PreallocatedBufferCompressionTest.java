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
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Snappy's preallocated-buffer subsystem for an application packet queue.
public class PreallocatedBufferCompressionTest {
    @Test
    @Timeout(60)
    public void compressesAndRestoresPayloadInsidePooledBuffers() {
        byte[] payload = createPayload();
        int sourceOffset = 19;
        byte[] sourceBuffer = new byte[sourceOffset + payload.length + 11];
        Arrays.fill(sourceBuffer, (byte) '|');
        System.arraycopy(payload, 0, sourceBuffer, sourceOffset, payload.length);

        int compressedOffset = 23;
        byte[] compressedBuffer = new byte[compressedOffset + Snappy.maxCompressedLength(payload.length) + 7];
        Arrays.fill(compressedBuffer, (byte) '|');
        int compressedLength = Snappy.compress(
                sourceBuffer, sourceOffset, payload.length, compressedBuffer, compressedOffset);

        int restoredOffset = 13;
        byte[] restoredBuffer = new byte[restoredOffset + payload.length + 5];
        int restoredLength = Snappy.uncompress(
                compressedBuffer, compressedOffset, compressedLength, restoredBuffer, restoredOffset);

        assertTrue(compressedLength < payload.length);
        assertEquals(payload.length, restoredLength);
        assertArrayEquals(payload, Arrays.copyOfRange(
                restoredBuffer, restoredOffset, restoredOffset + restoredLength));
        assertEquals('|', sourceBuffer[0]);
        assertEquals('|', compressedBuffer[compressedOffset + compressedLength]);
    }

    private static byte[] createPayload() {
        StringBuilder payload = new StringBuilder();
        for (int record = 0; record < 2048; record++) {
            payload.append("packet=").append(record)
                    .append("|queue=inventory|tenant=warehouse-denver")
                    .append("|operation=reserve|status=accepted\n");
        }
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }
}
