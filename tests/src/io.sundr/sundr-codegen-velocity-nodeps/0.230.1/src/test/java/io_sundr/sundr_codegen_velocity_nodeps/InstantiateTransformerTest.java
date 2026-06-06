/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.Transformer;
import io.sundr.deps.org.apache.commons.collections.functors.InstantiateTransformer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class InstantiateTransformerTest {
    private static final String UNSAFE_SERIALIZATION_PROPERTY =
            "io.sundr.deps.org.apache.commons.collections.enableUnsafeSerialization";

    @Test
    public void createsInstancesAndSupportsOptInSerialization() throws IOException, ClassNotFoundException {
        String previousValue = System.getProperty(UNSAFE_SERIALIZATION_PROPERTY);
        System.setProperty(UNSAFE_SERIALIZATION_PROPERTY, "true");
        try {
            Transformer transformer = InstantiateTransformer.getInstance(
                    new Class[] { String.class, Integer.class },
                    new Object[] { "alpha", 7 });

            ParameterizedConstructedValue created =
                    (ParameterizedConstructedValue) transformer.transform(ParameterizedConstructedValue.class);
            Transformer restored = (Transformer) deserialize(serialize(transformer));
            ParameterizedConstructedValue restoredCreated =
                    (ParameterizedConstructedValue) restored.transform(ParameterizedConstructedValue.class);

            assertThat(created.describe()).isEqualTo("alpha-7");
            assertThat(restoredCreated.describe()).isEqualTo("alpha-7");
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

    public static final class ParameterizedConstructedValue {
        private final String prefix;
        private final Integer number;

        public ParameterizedConstructedValue(String prefix, Integer number) {
            this.prefix = prefix;
            this.number = number;
        }

        public String describe() {
            return prefix + "-" + number;
        }
    }
}
