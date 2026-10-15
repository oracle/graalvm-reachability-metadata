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
import java.util.ArrayList;
import java.util.List;
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ObjectEncoderOutputStreamTest {
    @Test
    void writesLengthPrefixedObjectForNettyDecoder() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectEncoderOutputStream output = new ObjectEncoderOutputStream(bytes)) {
            output.writeObject(new ArrayList<>(List.of("topic-a", "topic-b")));
        }

        try (ObjectDecoderInputStream input =
                new ObjectDecoderInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(input.readObject()).isEqualTo(List.of("topic-a", "topic-b"));
        }
    }
}
