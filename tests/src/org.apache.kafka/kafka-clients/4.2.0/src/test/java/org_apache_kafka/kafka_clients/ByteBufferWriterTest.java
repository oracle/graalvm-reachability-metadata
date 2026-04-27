/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.kafka.shaded.com.google.protobuf.ByteString;
import org.apache.kafka.shaded.com.google.protobuf.UnsafeByteOperations;
import org.junit.jupiter.api.Test;

public class ByteBufferWriterTest {
    private static final MethodHandle WRITE_TO_RANGE_HANDLE = findWriteToRangeHandle();

    @Test
    void writesDirectByteStringRangeToFileOutputStream() throws Throwable {
        byte[] value = "kafka-clients".getBytes(StandardCharsets.UTF_8);
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(value.length);
        directBuffer.put(value);
        directBuffer.flip();
        ByteString byteString = UnsafeByteOperations.unsafeWrap(directBuffer);
        Path outputFile = Files.createTempFile("byte-buffer-writer", ".bin");

        try {
            try (OutputStream outputStream = new FileOutputStream(outputFile.toFile())) {
                WRITE_TO_RANGE_HANDLE.invokeWithArguments(byteString, outputStream, 2, 6);
            }

            assertThat(Files.readString(outputFile, StandardCharsets.UTF_8)).isEqualTo("fka-cl");
            assertThat(directBuffer.position()).isZero();
            assertThat(directBuffer.limit()).isEqualTo(value.length);
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    private static MethodHandle findWriteToRangeHandle() {
        try {
            return MethodHandles.privateLookupIn(ByteString.class, MethodHandles.lookup()).findVirtual(
                ByteString.class,
                "writeTo",
                MethodType.methodType(void.class, OutputStream.class, int.class, int.class)
            );
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
