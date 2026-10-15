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
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ObjectDecoderInputStreamTest {
    @Test
    void readsLengthPrefixedObjectWithConfiguredClassLoader() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectEncoderOutputStream output = new ObjectEncoderOutputStream(bytes, 128)) {
            output.writeObject(new LinkedHashMap<>(Map.of("partition", 7)));
        }

        try (ObjectDecoderInputStream input = new ObjectDecoderInputStream(
                new ByteArrayInputStream(bytes.toByteArray()), getClass().getClassLoader())) {
            assertThat(input.readObject()).isEqualTo(Map.of("partition", 7));
        }
    }
}
