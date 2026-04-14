/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_lz4.lz4_java;

import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class UnsafeUtilsTest {
    @Test
    void shouldLoadUnsafeUtilsWhenCreatingUnsafeFactoryAndRoundTripData() {
        LZ4Factory factory = LZ4Factory.unsafeInstance();
        byte[] input = ("unsafe lz4 factory initialization should exercise the unsafe utils"
                + " path and preserve the original payload after decompression")
                .getBytes(StandardCharsets.UTF_8);
        byte[] compressed = new byte[factory.fastCompressor().maxCompressedLength(input.length)];

        int compressedLength = factory.fastCompressor().compress(input, 0, input.length, compressed, 0, compressed.length);

        byte[] restored = new byte[input.length];
        int restoredLength = factory.safeDecompressor().decompress(compressed, 0, compressedLength, restored, 0);

        assertThat(restoredLength).isEqualTo(input.length);
        assertThat(restored).isEqualTo(input);
        assertThat(factory.toString()).contains("JavaUnsafe");
    }
}
