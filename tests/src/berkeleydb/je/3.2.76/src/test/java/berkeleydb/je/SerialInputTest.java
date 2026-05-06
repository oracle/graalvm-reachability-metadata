/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.serial.ClassCatalog;
import com.sleepycat.bind.serial.SerialInput;
import com.sleepycat.bind.serial.SerialOutput;
import com.sleepycat.je.DatabaseException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@code SerialInput} class resolution with and without an explicit class loader.
 */
public class SerialInputTest {

    @Test
    void resolvesSerializedClassWithExplicitClassLoader() throws Exception {
        InMemoryClassCatalog catalog = new InMemoryClassCatalog();
        byte[] serialized = serialize(new Payload("explicit-loader", 17), catalog);

        Payload payload = deserialize(serialized, catalog, Payload.class.getClassLoader());

        assertThat(payload.name).isEqualTo("explicit-loader");
        assertThat(payload.number).isEqualTo(17);
    }

    @Test
    void fallsBackToDefaultResolutionWhenExplicitClassLoaderCannotLoadClass() throws Exception {
        InMemoryClassCatalog catalog = new InMemoryClassCatalog();
        byte[] serialized = serialize(new Payload("fallback-loader", 23), catalog);
        ClassLoader rejectingLoader = new RejectingPayloadClassLoader(Payload.class.getClassLoader());

        Payload payload = deserialize(serialized, catalog, rejectingLoader);

        assertThat(payload.name).isEqualTo("fallback-loader");
        assertThat(payload.number).isEqualTo(23);
    }

    @Test
    void resolvesSerializedClassWithDefaultClassLoader() throws Exception {
        InMemoryClassCatalog catalog = new InMemoryClassCatalog();
        byte[] serialized = serialize(new Payload("default-loader", 31), catalog);

        Payload payload = deserialize(serialized, catalog, null);

        assertThat(payload.name).isEqualTo("default-loader");
        assertThat(payload.number).isEqualTo(31);
    }

    private static byte[] serialize(Payload payload, InMemoryClassCatalog catalog) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (SerialOutput output = new SerialOutput(bytes, catalog)) {
            output.writeObject(payload);
        }
        return bytes.toByteArray();
    }

    private static Payload deserialize(byte[] serialized,
                                       InMemoryClassCatalog catalog,
                                       ClassLoader classLoader) throws Exception {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (SerialInput input = new SerialInput(bytes, catalog, classLoader)) {
            Object object = input.readObject();
            assertThat(object).isInstanceOf(Payload.class);
            return (Payload) object;
        }
    }

    private static final class InMemoryClassCatalog implements ClassCatalog {
        private final Map<String, byte[]> idsByClassName = new HashMap<>();
        private final Map<ByteArrayKey, ObjectStreamClass> formatsById = new HashMap<>();
        private byte nextId = 1;

        @Override
        public void close() throws DatabaseException {
            // No resources are held by this in-memory catalog.
        }

        @Override
        public byte[] getClassID(ObjectStreamClass classDesc) throws DatabaseException, ClassNotFoundException {
            byte[] existingId = idsByClassName.get(classDesc.getName());
            if (existingId != null) {
                return existingId;
            }

            byte[] id = new byte[] { nextId++ };
            idsByClassName.put(classDesc.getName(), id);
            formatsById.put(new ByteArrayKey(id), classDesc);
            return id;
        }

        @Override
        public ObjectStreamClass getClassFormat(byte[] classID) throws DatabaseException, ClassNotFoundException {
            ObjectStreamClass format = formatsById.get(new ByteArrayKey(classID));
            assertThat(format).isNotNull();
            return format;
        }
    }

    private static final class ByteArrayKey {
        private final byte[] bytes;

        private ByteArrayKey(byte[] bytes) {
            this.bytes = bytes.clone();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof ByteArrayKey)) {
                return false;
            }
            ByteArrayKey other = (ByteArrayKey) object;
            return Arrays.equals(bytes, other.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }

    private static final class RejectingPayloadClassLoader extends ClassLoader {
        private RejectingPayloadClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (Payload.class.getName().equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }

    private static final class Payload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int number;

        private Payload(String name, int number) {
            this.name = name;
            this.number = number;
        }
    }
}
