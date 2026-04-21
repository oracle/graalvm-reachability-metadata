/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ListOrderedMapTest {

    @Test
    void serializesAndDeserializesEntriesInInsertionOrder() throws Exception {
        ListOrderedMap<String, Integer> original = new ListOrderedMap<>();
        original.put("beta", 2);
        original.put("alpha", 1);
        original.put("gamma", 3);
        original.put("alpha", 4);

        byte[] serialized = serialize(original);
        ListOrderedMap<String, Integer> restored = deserializeListOrderedMap(serialized);

        assertThat(restored.keyList()).containsExactly("beta", "alpha", "gamma");
        assertThat(restored.valueList()).containsExactly(2, 4, 3);
        assertThat(restored.firstKey()).isEqualTo("beta");
        assertThat(restored.lastKey()).isEqualTo("gamma");
        assertThat(restored.nextKey("alpha")).isEqualTo("gamma");
        assertThat(restored.previousKey("gamma")).isEqualTo("alpha");

        restored.put("delta", 5);

        assertThat(restored.keyList()).containsExactly("beta", "alpha", "gamma", "delta");
        assertThat(restored).containsEntry("alpha", 4).containsEntry("delta", 5);
    }

    private static byte[] serialize(ListOrderedMap<String, Integer> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static ListOrderedMap<String, Integer> deserializeListOrderedMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(ListOrderedMap.class);
            return (ListOrderedMap<String, Integer>) restored;
        }
    }
}
