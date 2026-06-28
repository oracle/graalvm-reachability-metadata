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
        ObjectMessage original = new ObjectMessage("persistent value");

        ObjectMessage restored = serializeAndDeserialize(original);

        assertThat(restored.getFormattedMessage()).isEqualTo("persistent value");
        assertThat(restored.getFormat()).isEqualTo("persistent value");
        assertThat(restored.getParameters()).containsExactly("persistent value");
    }

    @Test
    void serializesNonSerializableObjectAsItsStringRepresentation() throws Exception {
        Object value = new Object();
        ObjectMessage original = new ObjectMessage(value);

        ObjectMessage restored = serializeAndDeserialize(original);

        String expectedValue = value.toString();
        assertThat(restored.getFormattedMessage()).isEqualTo(expectedValue);
        assertThat(restored.getFormat()).isEqualTo(expectedValue);
        assertThat(restored.getParameters()).containsExactly(expectedValue);
    }

    private static ObjectMessage serializeAndDeserialize(ObjectMessage original) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ObjectMessage) input.readObject();
        }
    }
}
