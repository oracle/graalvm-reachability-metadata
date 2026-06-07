/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_airlift.aircompressor;

import io.airlift.compress.v2.zstd.ZstdJavaCompressor;
import io.airlift.compress.v2.zstd.ZstdJavaDecompressor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ZstdUnsafeUtilTest {
    @Test
    void compressesAndDecompressesPayload() {
        byte[] input = repeatedPayload();
        ZstdJavaCompressor compressor = new ZstdJavaCompressor();
        ZstdJavaDecompressor decompressor = new ZstdJavaDecompressor();

        byte[] compressed = new byte[compressor.maxCompressedLength(input.length)];
        int compressedSize = compressor.compress(
                input, 0, input.length,
                compressed, 0, compressed.length);
        assertThat(compressedSize).isPositive();

        byte[] actual = new byte[input.length];
        int restoredSize = decompressor.decompress(
                compressed, 0, compressedSize,
                actual, 0, actual.length);

        assertThat(restoredSize).isEqualTo(input.length);
        assertThat(actual).isEqualTo(input);
    }

    private static byte[] repeatedPayload() {
        String sentence = "Aircompressor Zstandard direct-buffer round trip exercises unsafe address access.\n";
        StringBuilder builder = new StringBuilder(sentence.length() * 64);
        for (int i = 0; i < 64; i++) {
            builder.append(sentence).append(i).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }
}
