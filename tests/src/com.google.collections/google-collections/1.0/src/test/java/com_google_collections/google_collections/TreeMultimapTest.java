/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.TreeMultimap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.AbstractMap.SimpleImmutableEntry;
import org.junit.jupiter.api.Test;

public class TreeMultimapTest {
    @Test
    void roundTripPreservesComparatorsAndSortedMappings() throws Exception {
        TreeMultimap<String, Integer> original = TreeMultimap.create();
        original.put("beta", 2);
        original.put("alpha", 3);
        original.put("alpha", 1);
        original.put("gamma", 4);
        original.put("alpha", 3);

        TreeMultimap<String, Integer> restored = roundTrip(original);

        assertThat(restored.keyComparator()).isNotNull();
        assertThat(restored.valueComparator()).isNotNull();
        assertThat(restored.entries()).containsExactly(
                entry("alpha", 1),
                entry("alpha", 3),
                entry("beta", 2),
                entry("gamma", 4));
        assertThat(restored.keySet()).containsExactly("alpha", "beta", "gamma");
        assertThat(restored.get("alpha")).containsExactly(1, 3);

        restored.put("alpha", 2);
        assertThat(restored.get("alpha")).containsExactly(1, 2, 3);
    }

    private static <K, V> SimpleImmutableEntry<K, V> entry(K key, V value) {
        return new SimpleImmutableEntry<K, V>(key, value);
    }

    private static <K, V> TreeMultimap<K, V> roundTrip(TreeMultimap<K, V> multimap)
            throws IOException, ClassNotFoundException {
        return deserialize(serialize(multimap));
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static <K, V> TreeMultimap<K, V> deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (TreeMultimap<K, V>) input.readObject();
        }
    }
}
