/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.map.UnmodifiableSortedMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

public class UnmodifiableSortedMapTest {

    @Test
    public void serializesAndDeserializesDecoratedSortedMapStateAndUnmodifiableViews()
            throws IOException, ClassNotFoundException {
        SortedMap<String, Integer> decorated = new TreeMap<>();
        decorated.put("alpha", 1);
        decorated.put("beta", 2);
        decorated.put("gamma", 3);

        SortedMap<String, Integer> original = UnmodifiableSortedMap.decorate(decorated);

        assertThat(original)
                .isInstanceOf(UnmodifiableSortedMap.class)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 2)
                .containsEntry("gamma", 3);
        assertThat(original.firstKey()).isEqualTo("alpha");
        assertThat(original.lastKey()).isEqualTo("gamma");
        assertThat(original.comparator()).isNull();
        assertThatThrownBy(() -> original.put("delta", 4))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] serialized = serialize(original);
        SortedMap<String, Integer> restored = deserializeSortedMap(serialized);

        assertThat(restored).isNotSameAs(original).isInstanceOf(UnmodifiableSortedMap.class);
        assertThat(restored)
                .hasSize(3)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 2)
                .containsEntry("gamma", 3);
        assertThat(restored.keySet()).containsExactly("alpha", "beta", "gamma");
        assertThat(restored.values()).containsExactly(1, 2, 3);
        assertThat(restored.firstKey()).isEqualTo("alpha");
        assertThat(restored.lastKey()).isEqualTo("gamma");
        assertThat(restored.comparator()).isNull();

        assertThat(restored.subMap("alpha", "gamma"))
                .isInstanceOf(UnmodifiableSortedMap.class)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 2);
        assertThat(restored.headMap("gamma"))
                .isInstanceOf(UnmodifiableSortedMap.class)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 2);
        assertThat(restored.tailMap("beta"))
                .isInstanceOf(UnmodifiableSortedMap.class)
                .containsEntry("beta", 2)
                .containsEntry("gamma", 3);

        assertThatThrownBy(() -> restored.put("delta", 4))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.putAll(Collections.singletonMap("delta", 4)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(restored::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.keySet().remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.values().remove(1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.entrySet().iterator().next().setValue(10))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(SortedMap<String, Integer> map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static SortedMap<String, Integer> deserializeSortedMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(SortedMap.class);
            return (SortedMap<String, Integer>) restored;
        }
    }
}
