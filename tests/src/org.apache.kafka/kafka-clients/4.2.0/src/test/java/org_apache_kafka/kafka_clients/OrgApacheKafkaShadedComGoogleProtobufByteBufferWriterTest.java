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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufByteBufferWriterTest {

    @Test
    void initializesDirectByteBufferOutputSupportForShadedProtobuf() throws Exception {
        byte[] payload = "kafka shaded protobuf direct buffer".getBytes(StandardCharsets.UTF_8);
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(payload.length);
        directBuffer.put(payload);
        directBuffer.flip();
        ByteString directBytes = UnsafeByteOperations.unsafeWrap(directBuffer);

        Class<?> writerClass = Class.forName(
                "org.apache.kafka.shaded.com.google.protobuf.ByteBufferWriter",
                true,
                directBytes.getClass().getClassLoader());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        directBytes.writeTo(output);

        assertThat(writerClass.getName()).endsWith("ByteBufferWriter");
        assertThat(output.toByteArray()).containsExactly(payload);
        assertThat(directBuffer.position()).isZero();
    }
}
