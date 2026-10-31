/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.protobuf.ByteString;
import com.couchbase.client.core.deps.com.google.protobuf.UnsafeByteOperations;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ByteBufferWriterTest {
    @Test
    void exercisesPublicApi() throws Exception {
        String text = "direct-buffer-data-".repeat(30);
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocateDirect(payload.length);
        buffer.put(payload).flip();
        ByteString bytes = UnsafeByteOperations.unsafeWrap(buffer).concat(ByteString.copyFromUtf8("tail"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bytes.writeTo(output);
        assertArrayEquals((text + "tail").getBytes(StandardCharsets.UTF_8), output.toByteArray());
        assertEquals(0, buffer.position());
    }

    @Test
    void writesDirectBufferSliceThroughFileChannel(@TempDir Path directory) throws Exception {
        byte[] payload = "header:binary-payload:footer".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocateDirect(payload.length);
        buffer.put(payload).flip();
        buffer.position("header:".length());
        buffer.limit(payload.length - ":footer".length());
        ByteString bytes = UnsafeByteOperations.unsafeWrap(buffer);
        Path file = directory.resolve("message.bin");
        try (FileOutputStream output = new FileOutputStream(file.toFile())) {
            output.getChannel();
            bytes.writeTo(output);
        }
        assertArrayEquals("binary-payload".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file));
        assertEquals("header:".length(), buffer.position());
    }
}
