/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import com.alibaba.nacos.shaded.com.google.protobuf.ByteString;
import com.alibaba.nacos.shaded.com.google.protobuf.UnsafeByteOperations;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class ByteBufferWriterTest {
    @Test
    void writesDirectBufferByteStringsToStreams() throws Exception {
        byte[] content = "registry-content".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocateDirect(content.length);
        buffer.put(content).flip();
        ByteString value = UnsafeByteOperations.unsafeWrap(buffer);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        value.writeTo(output);
        assertArrayEquals(content, output.toByteArray());
    }
}
