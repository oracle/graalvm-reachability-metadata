/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import at.yawk.lz4.Lz4;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Lz4_javaTest {
    @Test
    void shouldRoundTripHighlyCompressibleArray() {
        byte[] input = new byte[8192];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) ('A' + (i % 4));
        }

        byte[] compressed = new byte[Lz4.maxCompressedLength(input.length)];
        int compressedLength = Lz4.compress(input, compressed);

        byte[] restored = new byte[input.length];
        int decompressedLength = Lz4.decompressKnownSize(compressed, 0, compressedLength, restored, 0, restored.length);

        assertThat(decompressedLength).isEqualTo(input.length);
        assertThat(Arrays.copyOf(compressed, compressedLength).length).isLessThan(input.length);
        assertThat(restored).isEqualTo(input);
    }

    @Test
    void shouldRoundTripRandomArray() {
        byte[] input = new byte[4096];
        new Random(123456789L).nextBytes(input);

        byte[] compressed = new byte[Lz4.maxCompressedLength(input.length)];
        int compressedLength = Lz4.compress(input, compressed);

        byte[] restored = new byte[input.length];
        int decompressedLength = Lz4.decompressKnownSize(compressed, 0, compressedLength, restored, 0, restored.length);

        assertThat(decompressedLength).isEqualTo(input.length);
        assertThat(restored).isEqualTo(input);
    }

    @Test
    void shouldRoundTripEmptyArray() {
        byte[] input = new byte[0];
        byte[] compressed = new byte[Lz4.maxCompressedLength(input.length)];

        int compressedLength = Lz4.compress(input, compressed);
        byte[] restored = new byte[0];
        int decompressedLength = Lz4.decompressKnownSize(compressed, 0, compressedLength, restored, 0, restored.length);

        assertThat(compressedLength).isZero();
        assertThat(decompressedLength).isZero();
        assertThat(restored).isEqualTo(input);
    }

    @Test
    void shouldRoundTripDirectByteBuffers() {
        byte[] inputArray = new byte[2048];
        for (int i = 0; i < inputArray.length; i++) {
            inputArray[i] = (byte) (i % 31);
        }

        ByteBuffer input = ByteBuffer.allocateDirect(inputArray.length);
        input.put(inputArray);
        input.flip();

        ByteBuffer compressed = ByteBuffer.allocateDirect(Lz4.maxCompressedLength(input.remaining()));
        int compressedLength = Lz4.compress(input, compressed);

        compressed.flip();
        compressed.limit(compressedLength);

        ByteBuffer restored = ByteBuffer.allocateDirect(inputArray.length);
        int decompressedLength = Lz4.decompressKnownSize(compressed, restored);

        byte[] restoredArray = new byte[inputArray.length];
        restored.flip();
        restored.get(restoredArray);

        assertThat(decompressedLength).isEqualTo(inputArray.length);
        assertThat(restoredArray).isEqualTo(inputArray);
    }

    @Test
    void shouldCompressAndDecompressArraySlices() {
        byte[] source = new byte[300];
        for (int i = 0; i < source.length; i++) {
            source[i] = (byte) (i / 5);
        }

        int sourceOffset = 50;
        int sourceLength = 180;

        byte[] compressed = new byte[Lz4.maxCompressedLength(sourceLength) + 20];
        int compressedOffset = 7;
        int compressedLength = Lz4.compress(source, sourceOffset, sourceLength, compressed, compressedOffset, compressed.length - compressedOffset);

        byte[] restored = new byte[260];
        int restoredOffset = 30;
        int decompressedLength = Lz4.decompressKnownSize(
                compressed,
                compressedOffset,
                compressedLength,
                restored,
                restoredOffset,
                sourceLength
        );

        assertThat(decompressedLength).isEqualTo(sourceLength);
        assertThat(Arrays.copyOfRange(restored, restoredOffset, restoredOffset + sourceLength))
                .isEqualTo(Arrays.copyOfRange(source, sourceOffset, sourceOffset + sourceLength));
        assertThat(restored[restoredOffset - 1]).isZero();
        assertThat(restored[restoredOffset + sourceLength]).isZero();
    }

    @Test
    void shouldFailCompressionWhenDestinationIsTooSmall() {
        byte[] input = new byte[1024];
        Arrays.fill(input, (byte) 1);

        byte[] compressed = new byte[1];

        assertThatThrownBy(() -> Lz4.compress(input, 0, input.length, compressed, 0, compressed.length))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldFailToDecompressTruncatedInput() {
        byte[] input = new byte[2048];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i % 13);
        }

        byte[] compressed = new byte[Lz4.maxCompressedLength(input.length)];
        int compressedLength = Lz4.compress(input, compressed);

        byte[] truncated = Arrays.copyOf(compressed, compressedLength - 1);
        byte[] restored = new byte[input.length];

        assertThatThrownBy(() -> Lz4.decompressKnownSize(truncated, 0, truncated.length, restored, 0, restored.length))
                .isInstanceOf(RuntimeException.class);
    }
}
