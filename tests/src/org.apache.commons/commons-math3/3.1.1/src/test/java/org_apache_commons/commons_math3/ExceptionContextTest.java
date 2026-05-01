/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Locale;
import org.apache.commons.math3.exception.util.ExceptionContext;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.junit.jupiter.api.Test;

public class ExceptionContextTest {
    @Test
    void serializesMessagesThrowableAndContextValues() throws Exception {
        ExceptionContext context = new ExceptionContext(new IllegalArgumentException("outer failure"));
        NonSerializablePayload messagePayload = new NonSerializablePayload("message-payload");
        NonSerializablePayload contextPayload = new NonSerializablePayload("context-payload");

        context.addMessage(LocalizedFormats.DIMENSIONS_MISMATCH_2x2, 2, messagePayload, 4, 5);
        context.setValue("serializable-key", "serializable-value");
        context.setValue("nonserializable-key", contextPayload);

        ExceptionContext restored = roundTrip(context);

        assertThat(restored.getThrowable())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outer failure");
        assertThat(restored.getMessage(Locale.US))
                .contains("got 2x[Object could not be serialized:")
                .contains("NonSerializablePayload")
                .contains("but expected 4x5");
        assertThat(restored.getValue("serializable-key")).isEqualTo("serializable-value");
        assertThat(restored.getValue("nonserializable-key")).isInstanceOf(String.class);
        assertThat((String) restored.getValue("nonserializable-key"))
                .contains("NonSerializablePayload");
        assertThat(restored.getKeys()).containsExactlyInAnyOrder("serializable-key", "nonserializable-key");
    }

    private static ExceptionContext roundTrip(ExceptionContext context)
            throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(context);
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = input.readObject();
            assertThat(restored).isInstanceOf(ExceptionContext.class);
            return (ExceptionContext) restored;
        }
    }

    private static byte[] serialize(ExceptionContext context) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(context);
        }
        return bytes.toByteArray();
    }

    private static final class NonSerializablePayload {
        private final String name;

        private NonSerializablePayload(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
