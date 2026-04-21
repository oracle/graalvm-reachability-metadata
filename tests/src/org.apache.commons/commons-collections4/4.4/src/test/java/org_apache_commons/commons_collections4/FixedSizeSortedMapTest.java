/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Comparator;
import java.util.TreeMap;

import org.apache.commons.collections4.map.FixedSizeSortedMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FixedSizeSortedMapTest {

    @Test
    void serializesAndDeserializesFixedSizeSortedMapStateAndOrdering() throws Exception {
        TreeMap<String, Integer> decorated = new TreeMap<>(Comparator.reverseOrder());
        decorated.put("alpha", 1);
        decorated.put("beta", 2);
        decorated.put("gamma", 3);

        FixedSizeSortedMap<String, Integer> original = FixedSizeSortedMap.fixedSizeSortedMap(decorated);
        original.put("beta", 20);

        byte[] serialized = serialize(original);
        FixedSizeSortedMap<String, Integer> restored = deserializeFixedSizeSortedMap(serialized);

        assertThat(restored)
                .hasSize(3)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 20)
                .containsEntry("gamma", 3);
        assertThat(restored.keySet()).containsExactly("gamma", "beta", "alpha");
        assertThat(restored.firstKey()).isEqualTo("gamma");
        assertThat(restored.lastKey()).isEqualTo("alpha");
        assertThat(restored.comparator()).isNotNull();
        assertThat(restored.comparator().compare("gamma", "alpha")).isLessThan(0);
        assertThat(restored.isFull()).isTrue();
        assertThat(restored.maxSize()).isEqualTo(3);

        assertThat(restored.put("beta", 200)).isEqualTo(20);
        assertThat(restored).containsEntry("beta", 200);

        assertThatThrownBy(() -> restored.put("delta", 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Map is fixed size");
        assertThatThrownBy(() -> restored.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Map is fixed size");
        assertThatThrownBy(restored::clear)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Map is fixed size");
    }

    private static byte[] serialize(FixedSizeSortedMap<String, Integer> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static FixedSizeSortedMap<String, Integer> deserializeFixedSizeSortedMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(FixedSizeSortedMap.class);
            return (FixedSizeSortedMap<String, Integer>) restored;
        }
    }
}
