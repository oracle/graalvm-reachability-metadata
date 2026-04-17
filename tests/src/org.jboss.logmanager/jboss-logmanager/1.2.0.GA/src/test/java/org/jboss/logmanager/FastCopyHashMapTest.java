/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.logmanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

class FastCopyHashMapTest {

    @Test
    void preservesEntriesAcrossJavaSerialization() throws Exception {
        FastCopyHashMap<String, String> original = new FastCopyHashMap<>();
        original.put("alpha", "one");
        original.put(null, "null-key");
        original.put("nullable", null);

        FastCopyHashMap<String, String> restored = roundTrip(original);

        assertThat(restored)
                .isNotSameAs(original)
                .hasSize(3)
                .containsEntry("alpha", "one")
                .containsEntry(null, "null-key")
                .containsEntry("nullable", null);
    }

    private static FastCopyHashMap<String, String> roundTrip(FastCopyHashMap<String, String> map)
            throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(map);
        Object restored = deserialize(serialized);

        assertThat(restored).isInstanceOf(FastCopyHashMap.class);

        @SuppressWarnings("unchecked")
        FastCopyHashMap<String, String> typedRestored = (FastCopyHashMap<String, String>) restored;
        return typedRestored;
    }

    private static byte[] serialize(FastCopyHashMap<String, String> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    private static Object deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return objectInputStream.readObject();
        }
    }
}
