/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial_snappy.snappy_java;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.xerial.snappy.pure.PureJavaSnappy;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsafeUtilTest {

    @Test
    void pureJavaDirectByteBufferRoundTripUsesUnsafeAddressAccess() throws IOException {
        byte[] inputBytes = "PureJavaSnappy should round-trip direct byte buffers".repeat(32)
                .getBytes(StandardCharsets.UTF_8);
        PureJavaSnappy pureJavaSnappy = new PureJavaSnappy();
        ByteBuffer input = ByteBuffer.allocateDirect(inputBytes.length);
        ByteBuffer compressed = ByteBuffer.allocateDirect(pureJavaSnappy.maxCompressedLength(inputBytes.length));
        ByteBuffer restored = ByteBuffer.allocateDirect(inputBytes.length);

        input.put(inputBytes);
        input.flip();

        int compressedBytes = pureJavaSnappy.rawCompress(
                input,
                input.position(),
                input.remaining(),
                compressed,
                compressed.position());

        assertThat(compressedBytes).isPositive();
        assertThat(compressed.position()).isEqualTo(compressedBytes);

        compressed.flip();

        int restoredBytes = pureJavaSnappy.rawUncompress(
                compressed,
                compressed.position(),
                compressed.remaining(),
                restored,
                restored.position());

        assertThat(restoredBytes).isEqualTo(inputBytes.length);
        assertThat(restored.position()).isEqualTo(inputBytes.length);

        restored.flip();
        byte[] restoredBytesArray = new byte[restored.remaining()];
        restored.get(restoredBytesArray);

        assertThat(restoredBytesArray).containsExactly(inputBytes);
    }
}
