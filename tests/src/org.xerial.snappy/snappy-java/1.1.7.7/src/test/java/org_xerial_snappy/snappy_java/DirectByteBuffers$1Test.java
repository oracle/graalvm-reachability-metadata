/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial_snappy.snappy_java;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.xerial.snappy.SnappyFramedInputStream;
import org.xerial.snappy.SnappyFramedOutputStream;
import org.xerial.snappy.pool.BufferPool;
import org.xerial.snappy.pool.QuiescentBufferPool;

import static org.assertj.core.api.Assertions.assertThat;

public class DirectByteBuffers$1Test {

    @Test
    void framedRoundTripWithQuiescentPoolReleasesDirectBuffers() throws IOException {
        byte[] input = createPayload(200_000);
        BufferPool bufferPool = QuiescentBufferPool.getInstance();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();

        try (SnappyFramedOutputStream output = new SnappyFramedOutputStream(compressed, bufferPool)) {
            long written = output.transferFrom(new ByteArrayInputStream(input));

            assertThat(written).isEqualTo(input.length);
        }

        ByteArrayOutputStream restored = new ByteArrayOutputStream();

        try (SnappyFramedInputStream inputStream = new SnappyFramedInputStream(new ByteArrayInputStream(compressed.toByteArray()), bufferPool)) {
            long restoredBytes = inputStream.transferTo(restored);

            assertThat(restoredBytes).isEqualTo(input.length);
        }

        assertThat(restored.toByteArray()).containsExactly(input);
    }

    private static byte[] createPayload(int size) {
        byte[] payload = new byte[size];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) ('A' + (i % 23));
        }
        return payload;
    }
}
