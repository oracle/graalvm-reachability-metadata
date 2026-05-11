/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.serial.ClassCatalog;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import org.junit.jupiter.api.Test;

import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link SerialBinding} object serialization and deserialization.
 */
public class SerialBindingTest {

    @Test
    void serializesAndDeserializesObjectEntry() {
        InMemoryClassCatalog catalog = new InMemoryClassCatalog();
        SerialBinding binding = new SerialBinding(catalog, Payload.class);
        Payload input = new Payload("round-trip", 42);
        DatabaseEntry entry = new DatabaseEntry();

        binding.objectToEntry(input, entry);
        Object output = binding.entryToObject(entry);

        assertThat(entry.getOffset()).isGreaterThan(0);
        assertThat(entry.getSize()).isLessThan(entry.getData().length);
        assertThat(output).isInstanceOf(Payload.class);
        Payload payload = (Payload) output;
        assertThat(payload.name).isEqualTo("round-trip");
        assertThat(payload.number).isEqualTo(42);
    }

    @Test
    void rejectsObjectsOutsideBaseClass() {
        InMemoryClassCatalog catalog = new InMemoryClassCatalog();
        SerialBinding binding = new SerialBinding(catalog, Payload.class);

        assertThatThrownBy(() -> binding.objectToEntry("not-a-payload", new DatabaseEntry()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(Payload.class.getName());
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

            byte[] id = new byte[] {nextId++};
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
