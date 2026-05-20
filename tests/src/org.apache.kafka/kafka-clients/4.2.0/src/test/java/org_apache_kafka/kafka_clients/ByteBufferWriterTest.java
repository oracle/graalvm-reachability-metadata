/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteBufferWriterTest {

    @Test
    void writesDirectByteBufferBackedByteStringToOutputStream() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocateDirect(5);
        buffer.put(new byte[] {'k', 'a', 'f', 'k', 'a'});
        buffer.flip();
        ByteString bytes = ByteString.copyFrom(buffer);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        bytes.writeTo(output);

        assertThat(output.toByteArray()).containsExactly((byte) 'k', (byte) 'a', (byte) 'f', (byte) 'k', (byte) 'a');
    }
}
