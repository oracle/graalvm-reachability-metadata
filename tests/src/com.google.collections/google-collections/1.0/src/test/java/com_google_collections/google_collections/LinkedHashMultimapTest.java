/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.LinkedHashMultimap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class LinkedHashMultimapTest {
    @Test
    void roundTripPreservesKeyValueMappingsAndInsertionOrder() throws Exception {
        LinkedHashMultimap<String, Integer> original = LinkedHashMultimap.create();
        original.put("first", 1);
        original.put("second", 2);
        original.put("first", 3);
        original.put(null, 4);
        original.put("third", null);

        LinkedHashMultimap<String, Integer> restored = roundTrip(original);

        assertThat(restored.entries()).containsExactly(
                entry("first", 1),
                entry("second", 2),
                entry("first", 3),
                entry(null, 4),
                entry("third", null));
        assertThat(restored.keySet()).containsExactly("first", "second", null, "third");
        assertThat(restored.get("first")).containsExactly(1, 3);
    }

    @Test
    void roundTripPreservesOrderingAfterMutatingViews() throws Exception {
        LinkedHashMultimap<String, String> original = LinkedHashMultimap.create();
        original.put("alpha", "one");
        original.put("beta", "two");
        original.put("alpha", "three");
        original.remove("alpha", "one");
        original.put("gamma", "four");
        original.replaceValues("beta", Arrays.asList("five", "six"));

        LinkedHashMultimap<String, String> restored = roundTrip(original);

        assertThat(restored.entries()).containsExactly(
                entry("alpha", "three"),
                entry("gamma", "four"),
                entry("beta", "five"),
                entry("beta", "six"));
        assertThat(restored.values()).containsExactly("three", "four", "five", "six");
        assertThat(restored.get("beta")).containsExactly("five", "six");
    }

    private static <K, V> SimpleImmutableEntry<K, V> entry(K key, V value) {
        return new SimpleImmutableEntry<K, V>(key, value);
    }

    private static <K, V> LinkedHashMultimap<K, V> roundTrip(LinkedHashMultimap<K, V> multimap)
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
    private static <K, V> LinkedHashMultimap<K, V> deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (LinkedHashMultimap<K, V>) input.readObject();
        }
    }
}
