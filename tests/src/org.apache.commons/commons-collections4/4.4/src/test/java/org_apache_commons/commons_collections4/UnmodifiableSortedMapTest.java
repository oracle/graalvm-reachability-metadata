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
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.collections4.map.UnmodifiableSortedMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnmodifiableSortedMapTest {

    @Test
    void serializesAndDeserializesUnmodifiableSortedMapDecorator() throws Exception {
        TreeMap<String, Integer> delegate = new TreeMap<>();
        delegate.put("alpha", 1);
        delegate.put("beta", 2);
        delegate.put("delta", 4);
        delegate.put("gamma", 3);

        SortedMap<String, Integer> original = UnmodifiableSortedMap.unmodifiableSortedMap(delegate);

        assertThat(original)
                .isInstanceOf(UnmodifiableSortedMap.class)
                .hasSize(4)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 2)
                .containsEntry("delta", 4)
                .containsEntry("gamma", 3);
        assertThat(original.keySet()).containsExactly("alpha", "beta", "delta", "gamma");
        assertThat(original.firstKey()).isEqualTo("alpha");
        assertThat(original.lastKey()).isEqualTo("gamma");
        assertThatThrownBy(() -> original.put("epsilon", 5))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] serialized = serialize(original);
        SortedMap<String, Integer> restored = deserializeSortedMap(serialized);

        assertThat(restored)
                .isInstanceOf(UnmodifiableSortedMap.class)
                .hasSize(4)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 2)
                .containsEntry("delta", 4)
                .containsEntry("gamma", 3);
        assertThat(restored.comparator()).isNull();
        assertThat(restored.keySet()).containsExactly("alpha", "beta", "delta", "gamma");
        assertThat(restored.values()).containsExactly(1, 2, 4, 3);
        assertThat(restored.firstKey()).isEqualTo("alpha");
        assertThat(restored.lastKey()).isEqualTo("gamma");

        SortedMap<String, Integer> headMap = restored.headMap("delta");
        assertThat(headMap)
                .isInstanceOf(UnmodifiableSortedMap.class)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 2);
        assertThat(headMap.keySet()).containsExactly("alpha", "beta");

        SortedMap<String, Integer> subMap = restored.subMap("beta", "gamma");
        assertThat(subMap)
                .isInstanceOf(UnmodifiableSortedMap.class)
                .containsEntry("beta", 2)
                .containsEntry("delta", 4);
        assertThat(subMap.keySet()).containsExactly("beta", "delta");

        SortedMap<String, Integer> tailMap = restored.tailMap("beta");
        assertThat(tailMap)
                .isInstanceOf(UnmodifiableSortedMap.class)
                .containsEntry("beta", 2)
                .containsEntry("delta", 4)
                .containsEntry("gamma", 3);
        assertThat(tailMap.keySet()).containsExactly("beta", "delta", "gamma");

        assertThatThrownBy(() -> headMap.put("aardvark", 0))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> subMap.remove("beta"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> tailMap.clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.put("epsilon", 5))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(restored::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.entrySet().iterator().next().setValue(10))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(SortedMap<String, Integer> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static SortedMap<String, Integer> deserializeSortedMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(UnmodifiableSortedMap.class);
            return (SortedMap<String, Integer>) restored;
        }
    }
}
