/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_airlift.aircompressor;

import io.airlift.compress.lz4.Lz4Compressor;
import io.airlift.compress.lz4.Lz4Decompressor;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsafeUtilTest {
    @Test
    void compressesAndDecompressesDirectBuffers() {
        byte[] input = repeatedPayload();
        Lz4Compressor compressor = new Lz4Compressor();
        Lz4Decompressor decompressor = new Lz4Decompressor();

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
        String sentence = "Aircompressor LZ4 direct-buffer round trip exercises unsafe address access.\n";
        StringBuilder builder = new StringBuilder(sentence.length() * 64);
        for (int i = 0; i < 64; i++) {
            builder.append(sentence).append(i).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }
}
