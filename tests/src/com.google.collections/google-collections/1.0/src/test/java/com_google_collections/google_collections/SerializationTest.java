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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class SerializationTest {
    @Test
    void hashBiMapRoundTripPreservesKeysAndValues() throws Exception {
        HashBiMap<String, Integer> original = HashBiMap.create();
        original.put("alpha", 1);
        original.put("beta", 2);

        HashBiMap<String, Integer> restored = roundTrip(original);

        assertThat(restored).containsEntry("alpha", 1);
        assertThat(restored).containsEntry("beta", 2);
        assertThat(restored.inverse()).containsEntry(1, "alpha");
    }

    @Test
    void hashMultisetRoundTripPreservesElementCounts() throws Exception {
        HashMultiset<String> original = HashMultiset.create();
        original.add("alpha", 3);
        original.add("beta", 2);

        HashMultiset<String> restored = roundTrip(original);

        assertThat(restored.count("alpha")).isEqualTo(3);
        assertThat(restored.count("beta")).isEqualTo(2);
        assertThat(restored.size()).isEqualTo(5);
    }

    @Test
    void hashMultimapRoundTripPreservesDistinctKeysAndValues() throws Exception {
        HashMultimap<String, Integer> original = HashMultimap.create();
        original.put("alpha", 1);
        original.put("alpha", 2);
        original.put("beta", 3);

        HashMultimap<String, Integer> restored = roundTrip(original);

        assertThat(restored.get("alpha")).containsExactlyInAnyOrder(1, 2);
        assertThat(restored.get("beta")).containsExactly(3);
        assertThat(restored.size()).isEqualTo(3);
    }

    @Test
    void immutableMultisetRoundTripRestoresFinalSerializedFields() throws Exception {
        ImmutableMultiset<String> original = ImmutableMultiset.of("alpha", "alpha", "beta");

        ImmutableMultiset<String> restored = roundTrip(original);

        assertThat(restored.count("alpha")).isEqualTo(2);
        assertThat(restored.count("beta")).isEqualTo(1);
        assertThat(restored).containsExactly("alpha", "alpha", "beta");
    }

    private static <T extends Serializable> T roundTrip(T value)
            throws IOException, ClassNotFoundException {
        return deserialize(serialize(value));
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (T) input.readObject();
        }
    }
}
