/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.net.HardenedObjectInputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HardenedObjectInputStreamTest {

    @Test
    void readsWhitelistedSerializableObject() throws Exception {
        SerializablePayload payload = new SerializablePayload("logback", 7);
        byte[] serializedPayload = serialize(payload);

        try (HardenedObjectInputStream inputStream = new HardenedObjectInputStream(
                new ByteArrayInputStream(serializedPayload),
                List.of(SerializablePayload.class.getName()))) {
            Object deserializedObject = inputStream.readObject();

            assertThat(deserializedObject).isEqualTo(payload);
        }
    }

    @Test
    void rejectsNonWhitelistedSerializableObject() throws Exception {
        byte[] serializedPayload = serialize(new SerializablePayload("blocked", 1));

        try (HardenedObjectInputStream inputStream = new HardenedObjectInputStream(
                new ByteArrayInputStream(serializedPayload),
                new String[0])) {
            assertThatThrownBy(inputStream::readObject)
                    .isInstanceOf(InvalidClassException.class)
                    .hasMessageContaining("Unauthorized deserialization attempt")
                    .hasMessageContaining(SerializablePayload.class.getName());
        }
    }

    private static byte[] serialize(Serializable object) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(object);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static final class SerializablePayload implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String text;
        private final int value;

        private SerializablePayload(String text, int value) {
            this.text = text;
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SerializablePayload)) {
                return false;
            }
            SerializablePayload that = (SerializablePayload) other;
            return value == that.value && text.equals(that.text);
        }

        @Override
        public int hashCode() {
            int result = text.hashCode();
            result = 31 * result + value;
            return result;
        }
    }
}
