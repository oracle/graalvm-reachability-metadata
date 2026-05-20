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

import static org.assertj.core.api.Assertions.assertThat;

public class ByteBufferWriterTest {

    @Test
    void writesDirectByteBufferToOutputStream() throws Exception {
        ByteBuffer source = ByteBuffer.allocateDirect(4);
        source.put(new byte[] {1, 2, 3, 4});
        source.flip();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CodedOutputStream codedOutput = CodedOutputStream.newInstance(output);

        codedOutput.writeRawBytes(source);
        codedOutput.flush();

        assertThat(output.toByteArray()).containsExactly(1, 2, 3, 4);
    }
}
