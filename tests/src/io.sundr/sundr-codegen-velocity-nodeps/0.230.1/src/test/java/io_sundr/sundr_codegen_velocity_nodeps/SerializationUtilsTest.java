/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.lang.SerializationUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class SerializationUtilsTest {

    @Test
    public void serializesAndDeserializesSerializableObjects() {
        SerializableMessage message = new SerializableMessage("greeting", 7);

        byte[] serialized = SerializationUtils.serialize(message);
        SerializableMessage restored = (SerializableMessage)
                SerializationUtils.deserialize(serialized);

        assertThat(restored).isNotSameAs(message);
        assertThat(restored.text()).isEqualTo("greeting");
        assertThat(restored.priority()).isEqualTo(7);
    }

    @Test
    public void serializesToOutputStreamAndDeserializesFromInputStream() {
        SerializableMessage message = new SerializableMessage("stream", 11);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        SerializationUtils.serialize(message, output);
        SerializableMessage restored = (SerializableMessage)
                SerializationUtils.deserialize(new ByteArrayInputStream(output.toByteArray()));

        assertThat(restored).isNotSameAs(message);
        assertThat(restored.text()).isEqualTo("stream");
        assertThat(restored.priority()).isEqualTo(11);
    }

    public static final class SerializableMessage implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String text;
        private final int priority;

        public SerializableMessage(String text, int priority) {
            this.text = text;
            this.priority = priority;
        }

        public String text() {
            return text;
        }

        public int priority() {
            return priority;
        }
    }
}
