/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.io.ValidateObjectInputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidateObjectInputStreamTest {
    @Test
    public void resolvesAcceptedClassWhenReadingSerializedObject() throws Exception {
        Payload expected = new Payload("report", 3);
        byte[] serialized = serialize(expected);

        try (ValidateObjectInputStream inputStream = new ValidateObjectInputStream(
                new ByteArrayInputStream(serialized), Payload.class)) {
            Object restored = inputStream.readObject();

            assertThat(restored).isInstanceOf(Payload.class);
            Payload actual = (Payload) restored;
            assertThat(actual.name).isEqualTo(expected.name);
            assertThat(actual.priority).isEqualTo(expected.priority);
        }
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }

    private static final class Payload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;

        private final int priority;

        private Payload(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }
    }
}
