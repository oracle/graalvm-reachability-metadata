/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.jctools.maps.NonBlockingHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NonBlockingHashMapTest {

    @Test
    void serializationRoundTripPreservesEntries() throws IOException, ClassNotFoundException {
        NonBlockingHashMap<String, Integer> original = new NonBlockingHashMap<>();
        original.put("alpha", 1);
        original.put("beta", 2);

        NonBlockingHashMap<String, Integer> restored = roundTrip(original);

        assertThat(restored).containsExactlyInAnyOrderEntriesOf(original);
        assertThat(restored.put("gamma", 3)).isNull();
        assertThat(restored.get("gamma")).isEqualTo(3);
    }

    private static <K, V> NonBlockingHashMap<K, V> roundTrip(NonBlockingHashMap<K, V> map)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            @SuppressWarnings("unchecked")
            NonBlockingHashMap<K, V> restored = (NonBlockingHashMap<K, V>) input.readObject();
            return restored;
        }
    }
}
