/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.logging.log4j.message.ObjectMessage;
import org.junit.jupiter.api.Test;

public class ObjectMessageTest {
    @Test
    void serializesAndDeserializesSerializableObject() throws Exception {
        ObjectMessage original = new ObjectMessage("serializable value");

        ObjectMessage restored = serializeAndDeserialize(original);

        assertThat(restored.getFormattedMessage()).isEqualTo("serializable value");
        assertThat(restored.getFormat()).isEqualTo("serializable value");
        assertThat(restored.getParameters()).containsExactly("serializable value");
    }

    @Test
    void serializesNonSerializableObjectAsItsStringValue() throws Exception {
        ObjectMessage original = new ObjectMessage(new Object());
        String expectedValue = original.getFormattedMessage();

        ObjectMessage restored = serializeAndDeserialize(original);

        assertThat(restored.getFormattedMessage()).isEqualTo(expectedValue);
        assertThat(restored.getFormat()).isEqualTo(expectedValue);
        assertThat(restored.getParameters()).containsExactly(expectedValue);
    }

    private static ObjectMessage serializeAndDeserialize(ObjectMessage message) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(message);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ObjectMessage) input.readObject();
        }
    }
}
