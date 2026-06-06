/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.Factory;
import io.sundr.deps.org.apache.commons.collections.FactoryUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class InstantiateFactoryTest {
    private static final String UNSAFE_SERIALIZATION_PROPERTY =
            "io.sundr.deps.org.apache.commons.collections.enableUnsafeSerialization";

    @Test
    public void createsInstancesAndSupportsOptInSerialization() throws IOException, ClassNotFoundException {
        String previousValue = System.getProperty(UNSAFE_SERIALIZATION_PROPERTY);
        System.setProperty(UNSAFE_SERIALIZATION_PROPERTY, "true");
        try {
            Factory factory = FactoryUtils.instantiateFactory(FactoryUtils.class);

            Object created = factory.create();
            Factory restored = (Factory) deserialize(serialize(factory));
            Object restoredCreated = restored.create();

            assertThat(created).isInstanceOf(FactoryUtils.class);
            assertThat(restoredCreated).isInstanceOf(FactoryUtils.class);
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
}
