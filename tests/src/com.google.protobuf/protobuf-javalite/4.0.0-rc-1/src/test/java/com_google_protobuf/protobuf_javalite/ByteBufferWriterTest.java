/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

public class ByteBufferWriterTest {
    @Test
    void writesDirectByteBufferToOutputStreamWithoutChangingPosition() throws Exception {
        byte[] payload = new byte[] {0x10, 0x20, 0x30, 0x40};
        ByteBuffer source = ByteBuffer.allocateDirect(payload.length + 2);
        source.put((byte) 0x00);
        source.put(payload);
        source.put((byte) 0x7f);
        source.flip();
        source.position(1);
        source.limit(1 + payload.length);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writeDirectBuffer(source, output);

        assertThat(output.toByteArray()).containsExactly(payload);
        assertThat(source.position()).isEqualTo(1);
    }

    private static void writeDirectBuffer(ByteBuffer source, OutputStream output) throws Exception {
        Class<?> writerType = Class.forName(
                "com.google.protobuf.ByteBufferWriter",
                true,
                ByteString.class.getClassLoader());
        MethodHandle write = MethodHandles.privateLookupIn(writerType, MethodHandles.lookup())
                .findStatic(
                        writerType,
                        "write",
                        MethodType.methodType(void.class, ByteBuffer.class, OutputStream.class));
        try {
            write.invoke(source, output);
        } catch (Error | RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("ByteBufferWriter.write failed", e);
        }
    }
}
