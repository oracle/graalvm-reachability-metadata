/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Lz4_javaTest {
    @Test
    void shouldRoundTripDataWithSafeFactory() {
        LZ4Factory factory = LZ4Factory.safeInstance();
        byte[] input = "lz4 native image metadata".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = new byte[factory.fastCompressor().maxCompressedLength(input.length)];

        int compressedLength = factory.fastCompressor().compress(input, 0, input.length, compressed, 0, compressed.length);

        byte[] restored = new byte[input.length];
        int decompressedLength = factory.safeDecompressor().decompress(compressed, 0, compressedLength, restored, 0);

        assertThat(decompressedLength).isEqualTo(input.length);
        assertThat(restored).isEqualTo(input);
    }

    @Test
    void shouldRoundTripDataWithHighCompressor() {
        LZ4Factory factory = LZ4Factory.safeInstance();
        byte[] input = ("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                + "cccccccccccccccccccccccccccccccc"
                + "lz4 high compression path")
                .getBytes(StandardCharsets.UTF_8);
        byte[] compressed = new byte[factory.highCompressor().maxCompressedLength(input.length)];

        int compressedLength = factory.highCompressor().compress(input, 0, input.length, compressed, 0, compressed.length);

        byte[] restored = new byte[input.length];
        int decompressedLength = factory.safeDecompressor().decompress(compressed, 0, compressedLength, restored, 0);

        assertThat(decompressedLength).isEqualTo(input.length);
        assertThat(restored).isEqualTo(input);
    }

    @Test
    void shouldRoundTripDataWithFastDecompressor() {
        LZ4Factory factory = LZ4Factory.safeInstance();
        byte[] input = ("fast decompressor requires the original length"
                + " and should restore the compressed payload exactly")
                .getBytes(StandardCharsets.UTF_8);
        byte[] compressed = new byte[factory.fastCompressor().maxCompressedLength(input.length)];

        int compressedLength = factory.fastCompressor().compress(input, 0, input.length, compressed, 0, compressed.length);

        byte[] restored = new byte[input.length];
        int consumedLength = factory.fastDecompressor().decompress(compressed, 0, restored, 0, input.length);

        assertThat(consumedLength).isEqualTo(compressedLength);
        assertThat(restored).isEqualTo(input);
    }
}
