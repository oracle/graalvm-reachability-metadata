/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.Transformer;
import io.sundr.deps.org.apache.commons.collections.TransformerUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class CloneTransformerTest {
    private static final String UNSAFE_SERIALIZATION_PROPERTY =
            "io.sundr.deps.org.apache.commons.collections.enableUnsafeSerialization";

    @Test
    public void clonesInputAndSupportsOptInSerialization() throws IOException, ClassNotFoundException {
        String previousValue = System.getProperty(UNSAFE_SERIALIZATION_PROPERTY);
        System.setProperty(UNSAFE_SERIALIZATION_PROPERTY, "true");
        try {
            Transformer transformer = TransformerUtils.cloneTransformer();
            CloneableValue source = new CloneableValue("source");
            CloneableValue cloned = (CloneableValue) transformer.transform(source);

            Transformer restoredTransformer = (Transformer) deserialize(serialize(transformer));
            CloneableValue restoredClone = (CloneableValue) restoredTransformer.transform(source);

            assertThat(transformer.transform(null)).isNull();
            assertThat(cloned).isNotSameAs(source);
            assertThat(cloned.value()).isEqualTo("source-cloned");
            assertThat(restoredClone).isNotSameAs(source);
            assertThat(restoredClone.value()).isEqualTo("source-cloned");
        } finally {
            restoreProperty(previousValue);
        }
    }

    private static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }

    private static void restoreProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty(UNSAFE_SERIALIZATION_PROPERTY);
        } else {
            System.setProperty(UNSAFE_SERIALIZATION_PROPERTY, previousValue);
        }
    }

    public static final class CloneableValue implements Cloneable, Serializable {
        private static final long serialVersionUID = 1L;

        private final String value;

        public CloneableValue(String value) {
            this.value = value;
        }

        @Override
        public CloneableValue clone() {
            return new CloneableValue(value + "-cloned");
        }

        public String value() {
            return value;
        }
    }
}
