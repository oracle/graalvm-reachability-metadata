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
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class PrototypeFactoryTest {
    private static final String UNSAFE_SERIALIZATION_PROPERTY =
            "io.sundr.deps.org.apache.commons.collections.enableUnsafeSerialization";

    @Test
    public void createsPrototypeCopiesBySupportedStrategies() throws IOException, ClassNotFoundException {
        String previousValue = System.getProperty(UNSAFE_SERIALIZATION_PROPERTY);
        System.setProperty(UNSAFE_SERIALIZATION_PROPERTY, "true");
        try {
            CloneablePrototype cloneablePrototype = new CloneablePrototype("clone-source");
            Factory cloneFactory = FactoryUtils.prototypeFactory(cloneablePrototype);
            CloneablePrototype cloned = (CloneablePrototype) cloneFactory.create();
            Factory restoredCloneFactory = (Factory) deserialize(serialize(cloneFactory));
            CloneablePrototype restoredClone = (CloneablePrototype) restoredCloneFactory.create();

            CopyConstructedPrototype copyPrototype = new CopyConstructedPrototype("copy-source");
            Factory copyFactory = FactoryUtils.prototypeFactory(copyPrototype);
            CopyConstructedPrototype copied = (CopyConstructedPrototype) copyFactory.create();

            SerializedPrototype serializedPrototype = new SerializedPrototype("serialized-source");
            Factory serializationFactory = FactoryUtils.prototypeFactory(serializedPrototype);
            SerializedPrototype serializedCopy = (SerializedPrototype) serializationFactory.create();

            assertThat(cloned).isNotSameAs(cloneablePrototype);
            assertThat(cloned.value()).isEqualTo("clone-source-cloned");
            assertThat(restoredClone).isNotSameAs(cloneablePrototype);
            assertThat(restoredClone.value()).isEqualTo("clone-source-cloned");
            assertThat(copied).isNotSameAs(copyPrototype);
            assertThat(copied.value()).isEqualTo("copy-source-copied");
            assertThat(serializedCopy).isNotSameAs(serializedPrototype);
            assertThat(serializedCopy.value()).isEqualTo("serialized-source");
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

    public static final class CloneablePrototype implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String value;

        public CloneablePrototype(String value) {
            this.value = value;
        }

        @Override
        public CloneablePrototype clone() {
            return new CloneablePrototype(value + "-cloned");
        }

        public String value() {
            return value;
        }
    }

    public static final class CopyConstructedPrototype {
        private final String value;

        public CopyConstructedPrototype(String value) {
            this.value = value;
        }

        public CopyConstructedPrototype(CopyConstructedPrototype prototype) {
            this.value = prototype.value + "-copied";
        }

        public String value() {
            return value;
        }
    }

    public static final class SerializedPrototype implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String value;

        public SerializedPrototype(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
