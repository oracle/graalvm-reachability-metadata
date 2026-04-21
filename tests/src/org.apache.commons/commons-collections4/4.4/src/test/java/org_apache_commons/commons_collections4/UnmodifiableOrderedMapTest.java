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

import org.apache.commons.collections4.OrderedMap;
import org.apache.commons.collections4.OrderedMapIterator;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.collections4.map.UnmodifiableOrderedMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnmodifiableOrderedMapTest {

    @Test
    void serializesAndDeserializesUnmodifiableOrderedMapDecorator() throws Exception {
        ListOrderedMap<String, Integer> delegate = new ListOrderedMap<>();
        delegate.put("alpha", 1);
        delegate.put("beta", 2);
        delegate.put("gamma", 3);

        OrderedMap<String, Integer> original = UnmodifiableOrderedMap.unmodifiableOrderedMap(delegate);

        assertThat(original)
                .isInstanceOf(UnmodifiableOrderedMap.class)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 2)
                .containsEntry("gamma", 3);
        assertThat(original.firstKey()).isEqualTo("alpha");
        assertThat(original.lastKey()).isEqualTo("gamma");
        assertThat(original.nextKey("alpha")).isEqualTo("beta");
        assertThat(original.previousKey("gamma")).isEqualTo("beta");
        assertThatThrownBy(() -> original.put("delta", 4))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] serialized = serialize(original);
        OrderedMap<String, Integer> restored = deserializeOrderedMap(serialized);

        assertThat(restored)
                .isInstanceOf(UnmodifiableOrderedMap.class)
                .hasSize(3)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 2)
                .containsEntry("gamma", 3);
        assertThat(restored.firstKey()).isEqualTo("alpha");
        assertThat(restored.lastKey()).isEqualTo("gamma");
        assertThat(restored.nextKey("alpha")).isEqualTo("beta");
        assertThat(restored.previousKey("gamma")).isEqualTo("beta");
        assertThat(restored.keySet()).containsExactly("alpha", "beta", "gamma");
        assertThat(restored.values()).containsExactly(1, 2, 3);

        OrderedMapIterator<String, Integer> iterator = restored.mapIterator();
        assertThat(iterator.next()).isEqualTo("alpha");
        assertThat(iterator.getValue()).isEqualTo(1);
        assertThat(iterator.next()).isEqualTo("beta");
        assertThat(iterator.getValue()).isEqualTo(2);
        assertThat(iterator.next()).isEqualTo("gamma");
        assertThat(iterator.getValue()).isEqualTo(3);
        assertThat(iterator.previous()).isEqualTo("gamma");
        assertThat(iterator.getValue()).isEqualTo(3);

        assertThatThrownBy(() -> iterator.setValue(10))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(iterator::remove)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.put("delta", 4))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(restored::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.entrySet().iterator().next().setValue(10))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(OrderedMap<String, Integer> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static OrderedMap<String, Integer> deserializeOrderedMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(UnmodifiableOrderedMap.class);
            return (OrderedMap<String, Integer>) restored;
        }
    }
}
