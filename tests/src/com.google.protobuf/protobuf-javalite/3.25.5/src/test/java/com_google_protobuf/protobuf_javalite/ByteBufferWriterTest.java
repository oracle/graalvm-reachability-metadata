/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteBufferWriterAccess;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class ByteBufferWriterTest {
    @Test
    public void directBufferWritePreservesPositionAndCopiesRemainingBytes() throws IOException {
        String prefix = "protobuf ";
        String remaining = "direct byte buffer writer";
        byte[] payload = (prefix + remaining).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocateDirect(payload.length);
        buffer.put(payload);
        buffer.flip();
        buffer.position(prefix.length());

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ByteBufferWriterAccess.write(buffer, output);

        assertEquals(prefix.length(), buffer.position());
        assertArrayEquals(remaining.getBytes(StandardCharsets.UTF_8), output.toByteArray());
    }
}
