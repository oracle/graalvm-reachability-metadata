/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.map.FixedSizeSortedMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

public class FixedSizeSortedMapTest {

    @Test
    public void serializesAndDeserializesDecoratedSortedMapStateAndFixedSizeConstraints()
            throws IOException, ClassNotFoundException {
        SortedMap decorated = new TreeMap();
        decorated.put("alpha", 1);
        decorated.put("beta", 2);
        decorated.put("gamma", 3);

        SortedMap original = FixedSizeSortedMap.decorate(decorated);
        original.put("beta", 20);

        byte[] serialized = serialize(original);
        SortedMap restored = deserializeSortedMap(serialized);

        assertThat(restored).isNotSameAs(original).isInstanceOf(FixedSizeSortedMap.class);
        assertThat(restored)
                .hasSize(3)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 20)
                .containsEntry("gamma", 3);
        assertThat(restored.firstKey()).isEqualTo("alpha");
        assertThat(restored.lastKey()).isEqualTo("gamma");
        assertThat(restored.comparator()).isNull();

        FixedSizeSortedMap fixedSizeSortedMap = (FixedSizeSortedMap) restored;
        assertThat(fixedSizeSortedMap.isFull()).isTrue();
        assertThat(fixedSizeSortedMap.maxSize()).isEqualTo(3);

        assertThat(restored.put("alpha", 10)).isEqualTo(1);
        assertThat(restored).containsEntry("alpha", 10);
        assertThat(restored.subMap("alpha", "gamma"))
                .isInstanceOf(FixedSizeSortedMap.class)
                .containsEntry("alpha", 10)
                .containsEntry("beta", 20);
        assertThat(restored.headMap("gamma"))
                .isInstanceOf(FixedSizeSortedMap.class)
                .containsEntry("alpha", 10)
                .containsEntry("beta", 20);
        assertThat(restored.tailMap("beta"))
                .isInstanceOf(FixedSizeSortedMap.class)
                .containsEntry("beta", 20)
                .containsEntry("gamma", 3);

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

    private static byte[] serialize(SortedMap map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static SortedMap deserializeSortedMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(SortedMap.class);
            return (SortedMap) restored;
        }
    }
}
