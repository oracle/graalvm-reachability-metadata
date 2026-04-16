/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_io.commons_io;

import org.apache.commons.io.serialization.ValidatingObjectInputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidatingObjectInputStreamTest {
    @Test
    void deserializesAcceptedClasses() throws Exception {
        SerializablePayload expected = new SerializablePayload(7);
        byte[] serialized = serialize(expected);

        try (ValidatingObjectInputStream inputStream = new ValidatingObjectInputStream(new ByteArrayInputStream(serialized))) {
            inputStream.accept(SerializablePayload.class);

            Object deserialized = inputStream.readObject();

            assertThat(deserialized)
                    .isInstanceOf(SerializablePayload.class)
                    .isEqualTo(expected);
        }
    }

    @Test
    void rejectsExplicitlyRejectedClassesEvenWhenTheyWereAccepted() throws Exception {
        SerializablePayload payload = new SerializablePayload(11);
        byte[] serialized = serialize(payload);

        try (ValidatingObjectInputStream inputStream = new ValidatingObjectInputStream(new ByteArrayInputStream(serialized))) {
            inputStream.accept(SerializablePayload.class);
            inputStream.reject(SerializablePayload.class);

            assertThatThrownBy(inputStream::readObject)
                    .isInstanceOf(InvalidClassException.class)
                    .hasMessageContaining(SerializablePayload.class.getName());
        }
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }

    private static final class SerializablePayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final int value;

        private SerializablePayload(int value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SerializablePayload that)) {
                return false;
            }
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(value);
        }
    }
}
