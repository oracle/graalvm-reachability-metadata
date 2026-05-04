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

import org.apache.commons.math3.exception.util.ExceptionContext;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.junit.jupiter.api.Test;

public class ExceptionContextTest {

    @Test
    public void serializesMessagesAndContextValues() throws Exception {
        ExceptionContext original = new ExceptionContext(new IllegalArgumentException("root cause"));
        original.addMessage(LocalizedFormats.NUMBER_TOO_SMALL, 2, new NonSerializableValue("message-argument"));
        original.setValue("serializable", "kept");
        original.setValue("non-serializable", new NonSerializableValue("context-value"));

        ExceptionContext restored = roundTrip(original);

        assertThat(restored.getThrowable()).isInstanceOf(IllegalArgumentException.class);
        assertThat(restored.getThrowable()).hasMessage("root cause");
        assertThat(restored.getKeys()).containsExactlyInAnyOrder("serializable", "non-serializable");
        assertThat(restored.getValue("serializable")).isEqualTo("kept");
        assertThat(restored.getValue("non-serializable")).isEqualTo(nonSerializableReplacement());
        assertThat(restored.getMessage()).contains("2 is smaller than the minimum");
        assertThat(restored.getMessage()).contains(nonSerializableReplacement());
    }

    private static ExceptionContext roundTrip(ExceptionContext original) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ExceptionContext) input.readObject();
        }
    }

    private static String nonSerializableReplacement() {
        return "[Object could not be serialized: " + NonSerializableValue.class.getName() + "]";
    }

    private static final class NonSerializableValue {
        private final String label;

        private NonSerializableValue(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
