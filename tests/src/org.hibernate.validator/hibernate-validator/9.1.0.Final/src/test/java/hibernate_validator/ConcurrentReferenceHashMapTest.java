/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package hibernate_validator;

import org.hibernate.validator.internal.util.ConcurrentReferenceHashMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.validator.internal.util.ConcurrentReferenceHashMap.ReferenceType.STRONG;

public class ConcurrentReferenceHashMapTest {
    @Test
    void serializesEntriesAndRestoresThem() throws IOException, ClassNotFoundException {
        ConcurrentReferenceHashMap<String, String> map = new ConcurrentReferenceHashMap<>(16, STRONG, STRONG);
        map.put("language", "java");
        map.put("validator", "hibernate");

        byte[] serialized = serialize(map);
        ConcurrentReferenceHashMap<String, String> restored = deserialize(serialized);

        assertThat(restored).containsEntry("language", "java").containsEntry("validator", "hibernate");
        assertThat(restored).hasSize(2);
    }

    private static byte[] serialize(ConcurrentReferenceHashMap<String, String> map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentReferenceHashMap<String, String> deserialize(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (ConcurrentReferenceHashMap<String, String>) input.readObject();
        }
    }
}
