/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableListMultimap;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ImmutableListMultimapTest {
    @Test
    void serializesEntriesAndRestoresPerKeyValueOrder() throws Exception {
        ImmutableListMultimap.Builder<String, Integer> builder = ImmutableListMultimap.builder();
        ImmutableListMultimap<String, Integer> original = builder
                .put("first", 1)
                .put("second", 2)
                .put("first", 3)
                .putAll("third", 4, 5)
                .build();

        ImmutableListMultimap<String, Integer> restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(entryDescriptions(restored)).containsExactly(
                "first=1",
                "first=3",
                "second=2",
                "third=4",
                "third=5");
        assertThat(restored.keySet()).containsExactly("first", "second", "third");
        assertThat(restored.get("first")).containsExactly(1, 3);
        assertThat(restored.get("second")).containsExactly(2);
        assertThat(restored.get("third")).containsExactly(4, 5);
        assertThat(restored.get("missing")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (T) input.readObject();
        }
    }

    private static List<String> entryDescriptions(ImmutableListMultimap<String, Integer> multimap) {
        List<String> descriptions = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : multimap.entries()) {
            descriptions.add(entry.getKey() + "=" + entry.getValue());
        }
        return descriptions;
    }
}
