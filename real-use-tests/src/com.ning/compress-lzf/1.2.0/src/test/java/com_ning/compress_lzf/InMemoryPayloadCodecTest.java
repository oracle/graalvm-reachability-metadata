/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ning.compress_lzf;

import static org.assertj.core.api.Assertions.assertThat;

import com.ning.compress.lzf.LZFDecoder;
import com.ning.compress.lzf.LZFEncoder;
import com.ning.compress.lzf.LZFException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The in-memory payload codec subsystem is exercised by compressing a queued application batch.
 */
public class InMemoryPayloadCodecTest {
    @Test
    public void compressesAndRestoresAQueuedPayload() throws LZFException {
        byte[] batch = queuedPayload();
        byte[] encoded = LZFEncoder.safeEncode(batch, 0, batch.length);

        assertThat(encoded).hasSizeLessThan(batch.length);
        assertThat(LZFDecoder.safeDecode(encoded)).containsExactly(batch);
    }

    private static byte[] queuedPayload() {
        StringBuilder batch = new StringBuilder();
        for (int message = 0; message < 4096; message++) {
            batch.append("message-id=")
                    .append(message)
                    .append(" topic=inventory status=ready warehouse=7\n");
        }
        return batch.toString().getBytes(StandardCharsets.UTF_8);
    }
}
