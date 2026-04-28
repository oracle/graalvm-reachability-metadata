/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ning.compress_lzf;

import static org.assertj.core.api.Assertions.assertThat;

import com.ning.compress.lzf.ChunkDecoder;
import com.ning.compress.lzf.LZFEncoder;
import com.ning.compress.lzf.impl.UnsafeChunkDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class UnsafeChunkDecoderTest {
    @Test
    public void decodesDataCompressedBySafeEncoder() throws Exception {
        byte[] original = repeatedUtf8Payload();
        byte[] compressed = LZFEncoder.safeEncode(original);

        ChunkDecoder decoder = new UnsafeChunkDecoder();
        byte[] decoded = decoder.decode(compressed);

        assertThat(decoded).containsExactly(original);
    }

    private static byte[] repeatedUtf8Payload() {
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            payload.append("compress-lzf integration payload ")
                    .append(i % 16)
                    .append(" repeats to exercise unsafe chunk decoding.\n");
        }
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }
}
