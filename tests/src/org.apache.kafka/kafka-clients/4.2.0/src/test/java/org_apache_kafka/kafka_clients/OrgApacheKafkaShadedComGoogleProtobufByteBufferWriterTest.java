/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.CodedOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufByteBufferWriterTest {

    @Test
    void initializesShadedByteBufferWriterBeforeWritingDirectBuffers() throws Exception {
        Class<?> writerClass = Class.forName("org.apache.kafka.shaded.com.google.protobuf.ByteBufferWriter");

        ByteBuffer directBuffer = ByteBuffer.allocateDirect(4);
        directBuffer.put(new byte[] {1, 2, 3, 4});
        directBuffer.flip();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputStream, 1);
        codedOutputStream.writeByteBuffer(1, directBuffer);
        codedOutputStream.flush();

        assertThat(writerClass.getName()).isEqualTo("org.apache.kafka.shaded.com.google.protobuf.ByteBufferWriter");
        assertThat(directBuffer.position()).isZero();
        assertThat(Arrays.copyOfRange(outputStream.toByteArray(), 2, 6)).containsExactly(1, 2, 3, 4);
    }
}
