/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.net.HardenedObjectInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class HardenedObjectInputStreamTest {

    @Test
    void deserializesWhitelistedClassesThroughObjectInputStreamResolution() throws Exception {
        SerializablePayload payload = new SerializablePayload("logback-core");
        byte[] serializedPayload = serialize(payload);

        try (HardenedObjectInputStream inputStream = new HardenedObjectInputStream(
                new ByteArrayInputStream(serializedPayload),
                new String[] {SerializablePayload.class.getName()})) {
            Object restored = inputStream.readObject();

            assertThat(restored).isInstanceOf(SerializablePayload.class);
            assertThat(((SerializablePayload) restored).getValue()).isEqualTo("logback-core");
        }
    }

    private static byte[] serialize(Serializable value) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }

        return outputStream.toByteArray();
    }

    private static final class SerializablePayload implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String value;

        private SerializablePayload(String value) {
            this.value = value;
        }

        private String getValue() {
            return value;
        }
    }
}
