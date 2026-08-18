/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.ByteString;
import org.apache.kafka.shaded.com.google.protobuf.UnsafeByteOperations;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufByteBufferWriterTest {

    @Test
    void writesDirectByteBufferSliceThroughShadedProtobufOutputPath() throws Throwable {
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(8);
        directBuffer.put(new byte[] {10, 20, 30, 40, 50, 60, 70, 80});
        directBuffer.flip();
        ByteString byteString = UnsafeByteOperations.unsafeWrap(directBuffer);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writeToOutputStream(byteString, output, 1, 5);

        assertThat(output.toByteArray())
                .containsExactly((byte) 20, (byte) 30, (byte) 40, (byte) 50, (byte) 60);
        assertThat(directBuffer.position()).isZero();
        assertThat(byteString.asReadOnlyByteBuffer().position()).isZero();
    }

    private static void writeToOutputStream(ByteString byteString, ByteArrayOutputStream output,
            int sourceOffset, int numberToWrite) throws Throwable {
        Lookup lookup = MethodHandles.privateLookupIn(ByteString.class, MethodHandles.lookup());
        MethodHandle writeTo = lookup.findVirtual(ByteString.class, "writeTo",
                MethodType.methodType(void.class, OutputStream.class, int.class, int.class));
        writeTo.invoke(byteString, output, sourceOffset, numberToWrite);
    }
}
