/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_airlift.aircompressor;

import io.airlift.compress.lzo.LzoCompressor;
import io.airlift.compress.lzo.LzoDecompressor;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class LzoUnsafeUtilTest {
    @Test
    void compressesAndDecompressesDirectBuffers() {
        byte[] input = repeatedPayload();
        LzoCompressor compressor = new LzoCompressor();
        LzoDecompressor decompressor = new LzoDecompressor();

        ByteBuffer source = ByteBuffer.allocateDirect(input.length);
        source.put(input);
        source.flip();

        ByteBuffer compressed = ByteBuffer.allocateDirect(compressor.maxCompressedLength(input.length));
        compressor.compress(source, compressed);
        assertThat(compressed.position()).isPositive();

        compressed.flip();
        ByteBuffer restored = ByteBuffer.allocateDirect(input.length);
        decompressor.decompress(compressed, restored);

        restored.flip();
        byte[] actual = new byte[restored.remaining()];
        restored.get(actual);
        assertThat(actual).isEqualTo(input);
    }

    private static byte[] repeatedPayload() {
        String sentence = "Aircompressor LZO direct-buffer round trip exercises unsafe address access.\n";
        StringBuilder builder = new StringBuilder(sentence.length() * 64);
        for (int i = 0; i < 64; i++) {
            builder.append(sentence).append(i).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }
}
