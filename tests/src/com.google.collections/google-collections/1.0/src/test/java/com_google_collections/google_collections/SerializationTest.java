/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMultiset;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class SerializationTest {
    @Test
    void hashBiMapRoundTripPreservesEntries() throws Exception {
        HashBiMap<String, Integer> map = HashBiMap.create();
        map.put("one", 1);
        map.put("two", 2);

        HashBiMap<String, Integer> restored = roundTrip(map);

        assertThat(restored)
                .containsEntry("one", 1)
                .containsEntry("two", 2)
                .hasSize(2);
        assertThat(restored.inverse())
                .containsEntry(1, "one")
                .containsEntry(2, "two");
    }

    @Test
    void hashMultisetRoundTripPreservesElementCounts() throws Exception {
        HashMultiset<String> multiset = HashMultiset.create();
        multiset.add("apple", 2);
        multiset.add("pear", 3);

        HashMultiset<String> restored = roundTrip(multiset);

        assertThat(restored.count("apple")).isEqualTo(2);
        assertThat(restored.count("pear")).isEqualTo(3);
        assertThat(restored.size()).isEqualTo(5);
        assertThat(restored.elementSet()).containsExactlyInAnyOrder("apple", "pear");
    }

    @Test
    void hashMultimapRoundTripPreservesKeysAndValues() throws Exception {
        HashMultimap<String, String> multimap = HashMultimap.create();
        multimap.put("letters", "a");
        multimap.put("letters", "b");
        multimap.put("digits", "one");

        HashMultimap<String, String> restored = roundTrip(multimap);

        assertThat(restored.get("letters")).containsExactlyInAnyOrder("a", "b");
        assertThat(restored.get("digits")).containsExactly("one");
        assertThat(restored.size()).isEqualTo(3);
    }

    @Test
    void immutableMultisetRoundTripRestoresFinalFields() throws Exception {
        ImmutableMultiset<String> multiset = ImmutableMultiset.of(
                "red", "blue", "red", "green", "blue", "red");

        ImmutableMultiset<String> restored = roundTrip(multiset);

        assertThat(restored.count("red")).isEqualTo(3);
        assertThat(restored.count("blue")).isEqualTo(2);
        assertThat(restored.count("green")).isEqualTo(1);
        assertThat(restored.size()).isEqualTo(6);
        assertThat(restored.elementSet()).containsExactlyInAnyOrder("red", "blue", "green");
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
