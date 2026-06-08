/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.LinkedListMultimap;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LinkedListMultimapTest {
    @Test
    void serializesEntriesAndRestoresListMultimapIterationOrder() throws Exception {
        LinkedListMultimap<String, Integer> original = LinkedListMultimap.create();
        original.put("first", 1);
        original.put("second", 2);
        original.put("first", 3);
        original.put(null, 4);
        original.put("second", null);

        LinkedListMultimap<String, Integer> restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(entryDescriptions(restored)).containsExactly(
                "first=1",
                "second=2",
                "first=3",
                "null=4",
                "second=null");
        assertThat(restored.keySet()).containsExactly("first", "second", null);
        assertThat(restored.get("first")).containsExactly(1, 3);
        assertThat(restored.get("second")).containsExactly(2, null);
        assertThat(restored.get(null)).containsExactly(4);
        assertThat(restored.values()).containsExactly(1, 2, 3, 4, null);
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

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (T) input.readObject();
        }
    }

    private static List<String> entryDescriptions(LinkedListMultimap<String, Integer> multimap) {
        List<String> descriptions = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : multimap.entries()) {
            descriptions.add(entry.getKey() + "=" + entry.getValue());
        }
        return descriptions;
    }
}
