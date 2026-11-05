/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ning.compress_lzf;

import static org.assertj.core.api.Assertions.assertThat;

import com.ning.compress.lzf.LZFInputStream;
import com.ning.compress.lzf.LZFOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The streaming compression subsystem is exercised by this scenario.
 */
public class StreamingRoundTripTest {
    @Test
    public void streamsAnApplicationPayloadThroughLzf() throws IOException {
        byte[] original = applicationPayload();
        ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream();

        try (LZFOutputStream compressed = new LZFOutputStream(compressedBytes)) {
            compressed.write(new ByteArrayInputStream(original));
            compressed.finishBlock();
            assertThat(compressed.isOpen()).isTrue();
        }

        byte[] encoded = compressedBytes.toByteArray();
        assertThat(encoded.length).isLessThan(original.length);

        ByteArrayOutputStream restoredBytes = new ByteArrayOutputStream();
        try (LZFInputStream restored = new LZFInputStream(
                new ByteArrayInputStream(encoded), true)) {
            byte[] buffer = new byte[257];
            int totalRead = 0;
            int read;
            while ((read = restored.read(buffer)) != -1) {
                restoredBytes.write(buffer, 0, read);
                totalRead += read;
            }
            assertThat(totalRead).isEqualTo(original.length);
        }

        assertThat(restoredBytes.toByteArray()).containsExactly(original);
    }

    private static byte[] applicationPayload() {
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < 4096; i++) {
            payload.append("application event ")
                    .append(i % 32)
                    .append(" contains a repeated body for streaming storage.\n");
        }
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }
}
