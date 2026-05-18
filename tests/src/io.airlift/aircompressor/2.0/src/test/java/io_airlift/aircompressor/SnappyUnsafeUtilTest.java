/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_airlift.aircompressor;

import io.airlift.compress.v2.snappy.SnappyCompressor;
import io.airlift.compress.v2.snappy.SnappyDecompressor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class SnappyUnsafeUtilTest {
    @Test
    void compressesAndDecompressesDirectBuffers() {
        byte[] input = repeatedPayload();
        SnappyCompressor compressor = SnappyCompressor.create();
        SnappyDecompressor decompressor = SnappyDecompressor.create();

        byte[] compressed = new byte[compressor.maxCompressedLength(input.length)];
        int compressedLength = compressor.compress(input, 0, input.length, compressed, 0, compressed.length);
        assertThat(compressedLength).isPositive();

        byte[] restored = new byte[input.length];
        int restoredLength = decompressor.decompress(compressed, 0, compressedLength, restored, 0, restored.length);
        byte[] actual = new byte[restoredLength];
        System.arraycopy(restored, 0, actual, 0, restoredLength);
        assertThat(actual).isEqualTo(input);
    }

    private static byte[] repeatedPayload() {
        String sentence = "Aircompressor Snappy direct-buffer round trip exercises unsafe address access.\n";
        StringBuilder builder = new StringBuilder(sentence.length() * 64);
        for (int i = 0; i < 64; i++) {
            builder.append(sentence).append(i).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }
}
