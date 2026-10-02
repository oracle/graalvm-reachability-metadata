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

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufByteBufferWriterTest {

    @Test
    void writesDirectByteBufferThroughShadedProtobufOutputPath() throws Exception {
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(5);
        directBuffer.put(new byte[] {20, 30, 40, 50, 60});
        directBuffer.flip();
        ByteString byteString = UnsafeByteOperations.unsafeWrap(directBuffer);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        byteString.writeTo(output);

        assertThat(output.toByteArray())
                .containsExactly((byte) 20, (byte) 30, (byte) 40, (byte) 50, (byte) 60);
        assertThat(directBuffer.position()).isZero();
        assertThat(byteString.asReadOnlyByteBuffer().position()).isZero();
    }
}
