/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ning.compress_lzf;

import static org.assertj.core.api.Assertions.assertThat;

import com.ning.compress.lzf.ChunkEncoder;
import com.ning.compress.lzf.LZFDecoder;
import com.ning.compress.lzf.LZFEncoder;
import com.ning.compress.lzf.impl.UnsafeChunkEncoder;
import com.ning.compress.lzf.util.ChunkEncoderFactory;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class UnsafeChunkEncoderTest {
    @Test
    public void optimalEncoderCompressesRepeatingPayload() throws Exception {
        byte[] original = repeatedUtf8Payload();

        try (ChunkEncoder encoder = ChunkEncoderFactory.optimalInstance(original.length)) {
            assertThat(encoder).isInstanceOf(UnsafeChunkEncoder.class);

            byte[] compressed = LZFEncoder.encode(encoder, original, original.length);
            byte[] decoded = LZFDecoder.safeDecode(compressed);

            assertThat(compressed.length).isLessThan(original.length);
            assertThat(decoded).containsExactly(original);
        }
    }

    private static byte[] repeatedUtf8Payload() {
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < 512; i++) {
            payload.append("compress-lzf unsafe encoder integration payload ")
                    .append(i % 32)
                    .append(" repeats to exercise optimized chunk compression.\n");
        }
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }
}
