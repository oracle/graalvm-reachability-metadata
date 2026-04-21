/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractMultiValuedMapTest {

    @Test
    void serializesAndDeserializesMultiValuedEntries() throws Exception {
        ArrayListValuedHashMap<String, Integer> original = new ArrayListValuedHashMap<>();
        original.put("alpha", 1);
        original.put("alpha", 2);
        original.put("beta", 3);

        byte[] serialized = serialize(original);
        ArrayListValuedHashMap<String, Integer> restored = deserializeMultiValuedMap(serialized);

        assertThat(restored.size()).isEqualTo(3);
        assertThat(restored.keySet())
                .containsExactlyInAnyOrder("alpha", "beta");
        assertThat(restored.get("alpha"))
                .containsExactly(1, 2);
        assertThat(restored.get("beta"))
                .containsExactly(3);

        restored.put("alpha", 4);
        restored.put("gamma", 5);

        assertThat(restored.size()).isEqualTo(5);
        assertThat(restored.get("alpha"))
                .containsExactly(1, 2, 4);
        assertThat(restored.get("gamma"))
                .containsExactly(5);
    }

    private static byte[] serialize(ArrayListValuedHashMap<String, Integer> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static ArrayListValuedHashMap<String, Integer> deserializeMultiValuedMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(ArrayListValuedHashMap.class);
            return (ArrayListValuedHashMap<String, Integer>) restored;
        }
    }
}
