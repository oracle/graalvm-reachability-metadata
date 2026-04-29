/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_iq80_snappy.snappy;

import org.iq80.snappy.Snappy;
import org.iq80.snappy.SnappyFramedInputStream;
import org.iq80.snappy.SnappyFramedOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class SnappyInternalUtilsTest {
    @Test
    void compressesAndDecompressesBlockPayload() throws Exception {
        byte[] payload = createCompressiblePayload();

        byte[] compressed = Snappy.compress(payload);
        byte[] decompressed = Snappy.uncompress(compressed, 0, compressed.length);

        assertThat(compressed).isNotEmpty();
        assertThat(Snappy.getUncompressedLength(compressed, 0)).isEqualTo(payload.length);
        assertThat(decompressed).isEqualTo(payload);
    }

    @Test
    void writesAndReadsSnappyStreamPayload() throws Exception {
        byte[] payload = createCompressiblePayload();
        ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();

        try (SnappyFramedOutputStream snappyOutput = new SnappyFramedOutputStream(compressedOutput)) {
            snappyOutput.write(payload, 0, 256);
            snappyOutput.write(payload, 256, payload.length - 256);
        }

        byte[] compressed = compressedOutput.toByteArray();
        byte[] decompressed = readSnappyStream(compressed);

        assertThat(compressed).isNotEmpty();
        assertThat(decompressed).isEqualTo(payload);
    }

    private static byte[] readSnappyStream(byte[] compressed) throws IOException {
        try (SnappyFramedInputStream snappyInput = new SnappyFramedInputStream(new ByteArrayInputStream(compressed))) {
            return snappyInput.readAllBytes();
        }
    }

    private static byte[] createCompressiblePayload() {
        byte[] phrase = "snappy dynamic access coverage payload ".getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[phrase.length * 128];

        for (int offset = 0; offset < payload.length; offset += phrase.length) {
            System.arraycopy(phrase, 0, payload, offset, phrase.length);
        }
        Arrays.fill(payload, 64, 128, (byte) 'x');
        return payload;
    }
}
