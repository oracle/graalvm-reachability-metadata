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

/// Exercises Snappy's compressed-buffer capacity planning subsystem for an application message batch.
public class CompressionCapacityPlanningTest {
    @Test
    @Timeout(60)
    public void allocatesBoundedSlotsAndRestoresEachMessage() {
        byte[][] messages = createMessages();
        byte[][] compressedMessages = new byte[messages.length][];

        for (int message = 0; message < messages.length; message++) {
            byte[] payload = messages[message];
            int capacity = Snappy.maxCompressedLength(payload.length);
            compressedMessages[message] = new byte[capacity];
            int compressedLength = Snappy.compress(
                    payload, 0, payload.length, compressedMessages[message], 0);

            assertTrue(compressedLength <= capacity);
            compressedMessages[message] = Arrays.copyOf(
                    compressedMessages[message], compressedLength);
        }

        for (int message = 0; message < messages.length; message++) {
            byte[] compressed = compressedMessages[message];
            assertEquals(messages[message].length, Snappy.getUncompressedLength(compressed, 0));
            byte[] restored = Snappy.uncompress(compressed, 0, compressed.length);
            assertArrayEquals(messages[message], restored);
        }
    }

    private static byte[][] createMessages() {
        byte[][] messages = new byte[4][];
        for (int message = 0; message < messages.length; message++) {
            StringBuilder value = new StringBuilder();
            for (int item = 0; item < 512 + message * 97; item++) {
                value.append("message=").append(message)
                        .append("|item=").append(item)
                        .append("|service=checkout|status=accepted|warehouse=denver\n");
            }
            messages[message] = value.toString().getBytes(StandardCharsets.UTF_8);
        }
        return messages;
    }
}
