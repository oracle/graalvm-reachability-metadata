/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

import org.apache.kafka.shaded.com.google.protobuf.ByteString;
import org.apache.kafka.shaded.com.google.protobuf.UnsafeByteOperations;
import org.junit.jupiter.api.Test;

public class ByteBufferWriterTest {
    private static final MethodHandle WRITE_TO_RANGE = writeToRangeHandle();

    @Test
    void writesDirectByteBufferRangeThroughByteStringOutputPath() throws Throwable {
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(8);
        directBuffer.put(new byte[] {10, 20, 30, 40, 50, 60, 70, 80});
        directBuffer.flip();

        ByteString byteString = UnsafeByteOperations.unsafeWrap(directBuffer);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writeToRange(byteString, output, 2, 4);

        assertArrayEquals(new byte[] {30, 40, 50, 60}, output.toByteArray());
        assertEquals(0, directBuffer.position());
    }

    private static void writeToRange(
            ByteString byteString,
            OutputStream output,
            int offset,
            int length) throws Throwable {
        WRITE_TO_RANGE.invoke(byteString, output, offset, length);
    }

    private static MethodHandle writeToRangeHandle() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ByteString.class, MethodHandles.lookup());
            MethodType methodType = MethodType.methodType(void.class, OutputStream.class, int.class, int.class);
            return lookup.findVirtual(ByteString.class, "writeTo", methodType);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Expected ByteString range write method to be available", exception);
        }
    }
}
