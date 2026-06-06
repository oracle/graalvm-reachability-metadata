/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.OrderedMap;
import io.sundr.deps.org.apache.commons.collections.OrderedMapIterator;
import io.sundr.deps.org.apache.commons.collections.map.ListOrderedMap;
import io.sundr.deps.org.apache.commons.collections.map.UnmodifiableOrderedMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class UnmodifiableOrderedMapTest {

    @Test
    public void serializesAndDeserializesOrderedMapAsUnmodifiableOrderedMap()
            throws IOException, ClassNotFoundException {
        ListOrderedMap decorated = new ListOrderedMap();
        decorated.put("alpha", "one");
        decorated.put("beta", "two");
        decorated.put("gamma", "three");
        OrderedMap original = UnmodifiableOrderedMap.decorate(decorated);

        assertOrderedContents(original);
        assertMutationOperationsAreRejected(original);

        byte[] serialized = serialize(original);
        OrderedMap restored = deserialize(serialized);

        assertThat(restored).isNotSameAs(original).isInstanceOf(UnmodifiableOrderedMap.class);
        assertOrderedContents(restored);
        assertMutationOperationsAreRejected(restored);
    }

    private static void assertOrderedContents(OrderedMap map) {
        assertThat(map).hasSize(3);
        assertThat(map.firstKey()).isEqualTo("alpha");
        assertThat(map.lastKey()).isEqualTo("gamma");
        assertThat(map.nextKey("alpha")).isEqualTo("beta");
        assertThat(map.previousKey("gamma")).isEqualTo("beta");
        assertThat(map.keySet()).containsExactly("alpha", "beta", "gamma");
        assertThat(map.values()).containsExactly("one", "two", "three");

        OrderedMapIterator iterator = map.orderedMapIterator();
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isEqualTo("alpha");
        assertThat(iterator.getValue()).isEqualTo("one");
        assertThat(iterator.next()).isEqualTo("beta");
        assertThat(iterator.getValue()).isEqualTo("two");
        assertThat(iterator.next()).isEqualTo("gamma");
        assertThat(iterator.getValue()).isEqualTo("three");
        assertThat(iterator.hasNext()).isFalse();
    }

    private static void assertMutationOperationsAreRejected(OrderedMap map) {
        assertThatThrownBy(() -> map.put("delta", "four"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(map::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.putAll(new ListOrderedMap()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.orderedMapIterator().setValue("changed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(OrderedMap map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static OrderedMap deserialize(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(OrderedMap.class);
            return (OrderedMap) restored;
        }
    }
}
