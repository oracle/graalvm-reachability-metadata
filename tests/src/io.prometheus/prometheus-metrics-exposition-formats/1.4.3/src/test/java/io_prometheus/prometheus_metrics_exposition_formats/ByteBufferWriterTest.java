/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.prometheus_metrics_exposition_formats;

import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.ByteString;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.UnsafeByteOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteBufferWriterTest {
    @TempDir
    Path directory;

    @Test
    void writesDirectByteStringToFileAndBufferedStream() throws Exception {
        byte[] expected = "metric payload".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocateDirect(expected.length + 2);
        buffer.put((byte) 0).put(expected).put((byte) 0).flip();
        buffer.position(1);
        buffer.limit(expected.length + 1);
        ByteString bytes = UnsafeByteOperations.unsafeWrap(buffer);
        Path file = directory.resolve("metric.bin");

        try (FileOutputStream output = new FileOutputStream(file.toFile())) {
            bytes.writeTo(output);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bytes.writeTo(output);

        assertThat(Files.readAllBytes(file)).isEqualTo(expected);
        assertThat(output.toByteArray()).isEqualTo(expected);
        assertThat(bytes.toByteArray()).isEqualTo(expected);
        assertThat(buffer.position()).isEqualTo(1);
    }
}
