/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableSetMultimap;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ImmutableSetMultimapTest {
    @Test
    void serializesKeysAndDistinctValues() throws Exception {
        ImmutableSetMultimap<String, Integer> original = ImmutableSetMultimap.<String, Integer>builder()
                .put("letters", 1)
                .put("letters", 2)
                .put("letters", 2)
                .put("digits", 10)
                .build();

        ImmutableSetMultimap<String, Integer> restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.keySet()).containsExactly("letters", "digits");
        assertThat(restored.get("letters")).containsExactly(1, 2);
        assertThat(restored.get("digits")).containsExactly(10);
        assertThat(restored.get("missing")).isEmpty();
        assertThat(entryDescriptions(restored)).containsExactly(
                "letters=1",
                "letters=2",
                "digits=10");
    }

    @SuppressWarnings("unchecked")
    private static <K, V> ImmutableSetMultimap<K, V> roundTrip(ImmutableSetMultimap<K, V> multimap)
            throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(multimap);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = input.readObject();
            assertThat(restored).isInstanceOf(ImmutableSetMultimap.class);
            return (ImmutableSetMultimap<K, V>) restored;
        }
    }

    private static List<String> entryDescriptions(ImmutableSetMultimap<String, Integer> multimap) {
        List<String> descriptions = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : multimap.entries()) {
            descriptions.add(entry.getKey() + "=" + entry.getValue());
        }
        return descriptions;
    }
}
