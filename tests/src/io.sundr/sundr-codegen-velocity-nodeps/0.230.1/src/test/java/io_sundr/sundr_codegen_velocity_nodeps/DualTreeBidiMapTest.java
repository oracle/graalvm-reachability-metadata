/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.OrderedMapIterator;
import io.sundr.deps.org.apache.commons.collections.SortedBidiMap;
import io.sundr.deps.org.apache.commons.collections.bidimap.DualTreeBidiMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class DualTreeBidiMapTest {

    @Test
    public void serializesPopulatedDualTreeBidiMapUsingItsPublicSerializationForm()
            throws IOException, ClassNotFoundException {
        DualTreeBidiMap original = new DualTreeBidiMap();
        original.put("bravo", "two");
        original.put("alpha", "one");
        original.put("charlie", "three");

        byte[] serialized = serialize(original);
        DualTreeBidiMap restored = deserialize(serialized);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(3);
        assertThat(restored.firstKey()).isEqualTo("alpha");
        assertThat(restored.lastKey()).isEqualTo("charlie");
        assertThat(restored.nextKey("alpha")).isEqualTo("bravo");
        assertThat(restored.previousKey("charlie")).isEqualTo("bravo");
        assertThat(restored.get("alpha")).isEqualTo("one");
        assertThat(restored.getKey("two")).isEqualTo("bravo");

        OrderedMapIterator iterator = restored.orderedMapIterator();
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isEqualTo("alpha");
        assertThat(iterator.getValue()).isEqualTo("one");

        SortedBidiMap inverse = restored.inverseSortedBidiMap();
        assertThat(inverse.firstKey()).isEqualTo("one");
        assertThat(inverse.get("three")).isEqualTo("charlie");
    }

    private static byte[] serialize(DualTreeBidiMap map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static DualTreeBidiMap deserialize(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            return (DualTreeBidiMap) inputStream.readObject();
        }
    }
}
