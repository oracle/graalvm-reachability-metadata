/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ning.compress_lzf;

import static org.assertj.core.api.Assertions.assertThat;

import com.ning.compress.BufferRecycler;
import com.ning.compress.lzf.LZFDecoder;
import com.ning.compress.lzf.LZFEncoder;
import com.ning.compress.lzf.LZFException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The reusable-buffer codec subsystem is exercised by encoding a message into a response buffer.
 */
public class ReusableBufferCodecTest {
    @Test
    public void appendsAndDecodesAQueuedMessageUsingReusableBuffers() throws LZFException {
        byte[] message = queuedMessage();
        BufferRecycler recycler = new BufferRecycler();
        byte[] response = recycler.allocOutputBuffer(message.length + 8192);
        int responseHeaderLength = 12;
        int encodedEnd = LZFEncoder.safeAppendEncoded(
                message, 0, message.length, response, responseHeaderLength, recycler);
        byte[] restored = recycler.allocDecodeBuffer(message.length);

        try {
            int restoredLength = LZFDecoder.safeDecode(
                    response, responseHeaderLength, encodedEnd - responseHeaderLength, restored);

            assertThat(encodedEnd - responseHeaderLength).isLessThan(message.length);
            assertThat(restoredLength).isEqualTo(message.length);
            assertThat(restored).containsExactly(message);
        } finally {
            recycler.releaseDecodeBuffer(restored);
            recycler.releaseOutputBuffer(response);
        }
    }

    private static byte[] queuedMessage() {
        StringBuilder message = new StringBuilder();
        for (int item = 0; item < 4096; item++) {
            message.append("item=")
                    .append(item)
                    .append(" warehouse=7 state=ready operation=replenish\n");
        }
        return message.toString().getBytes(StandardCharsets.UTF_8);
    }
}
