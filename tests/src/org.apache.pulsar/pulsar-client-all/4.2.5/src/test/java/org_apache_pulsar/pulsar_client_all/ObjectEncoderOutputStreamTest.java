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

@SuppressWarnings("deprecation")
public class ObjectEncoderOutputStreamTest {

    @Test
    void framesAndRestoresSerializableObjects() throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectEncoderOutputStream output = new ObjectEncoderOutputStream(bytes, 64)) {
            output.writeObject(new ArrayList<>(List.of("one", "two", "three")));
        }

        try (ObjectDecoderInputStream input =
                new ObjectDecoderInputStream(new ByteArrayInputStream(bytes.toByteArray()), 4096)) {
            assertThat(input.readObject()).isEqualTo(List.of("one", "two", "three"));
        }
    }
}
