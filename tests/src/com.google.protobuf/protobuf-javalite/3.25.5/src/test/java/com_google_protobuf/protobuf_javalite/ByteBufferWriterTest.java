/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import com.google.protobuf.ByteBufferWriterCoverageSupport;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteBufferWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesDirectBufferToOutputStreamWithoutMovingPosition() throws Exception {
        byte[] payload = new byte[] {10, 20, 30, 40};
        ByteBuffer buffer = directBufferContaining(payload);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ByteBufferWriterCoverageSupport.write(buffer, output);

        assertThat(output.toByteArray()).containsExactly(payload);
        assertThat(buffer.position()).isEqualTo(1);
        assertThat(buffer.remaining()).isEqualTo(payload.length);
    }

    @Test
    void writesDirectBufferToFileOutputStream() throws Exception {
        byte[] payload = new byte[] {1, 1, 2, 3, 5, 8};
        ByteBuffer buffer = directBufferContaining(payload);
        Path outputFile = tempDir.resolve("byte-buffer-writer-output.bin");

        try (FileOutputStream output = new FileOutputStream(outputFile.toFile())) {
            ByteBufferWriterCoverageSupport.write(buffer, output);
        }

        assertThat(Files.readAllBytes(outputFile)).containsExactly(payload);
        assertThat(buffer.position()).isEqualTo(1);
    }

    private static ByteBuffer directBufferContaining(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(payload.length + 2);
        buffer.put((byte) -1);
        buffer.put(payload);
        buffer.put((byte) -2);
        buffer.flip();
        buffer.position(1);
        buffer.limit(1 + payload.length);
        return buffer;
    }
}
