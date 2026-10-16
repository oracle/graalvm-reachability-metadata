/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class ObjectEncoderOutputStreamTest {
    @Test
    void writesFramedSerializedObjects() throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectEncoderOutputStream output = new ObjectEncoderOutputStream(bytes)) {
            output.writeObject("pulsar");
        }

        try (ObjectDecoderInputStream input = new ObjectDecoderInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(input.readObject()).isEqualTo("pulsar");
        }
    }
}
