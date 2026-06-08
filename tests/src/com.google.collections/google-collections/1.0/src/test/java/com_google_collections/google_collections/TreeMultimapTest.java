/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.TreeMultimap;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TreeMultimapTest {
    @Test
    void serializesComparatorsAndRestoresSortedKeyValueIteration() throws Exception {
        TreeMultimap<String, Integer> original = TreeMultimap.create(
                Collections.reverseOrder(),
                Collections.reverseOrder());
        original.put("bravo", 2);
        original.put("alpha", 5);
        original.put("charlie", 1);
        original.put("bravo", 3);
        original.put("bravo", 1);

        TreeMultimap<String, Integer> restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.keySet()).containsExactly("charlie", "bravo", "alpha");
        assertThat(restored.get("bravo")).containsExactly(3, 2, 1);
        assertThat(entryDescriptions(restored)).containsExactly(
                "charlie=1",
                "bravo=3",
                "bravo=2",
                "bravo=1",
                "alpha=5");

        restored.put("delta", 0);
        restored.put("bravo", 4);

        assertThat(restored.keySet()).containsExactly("delta", "charlie", "bravo", "alpha");
        assertThat(restored.get("bravo")).containsExactly(4, 3, 2, 1);
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

    private static List<String> entryDescriptions(TreeMultimap<String, Integer> multimap) {
        List<String> descriptions = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : multimap.entries()) {
            descriptions.add(entry.getKey() + "=" + entry.getValue());
        }
        return descriptions;
    }
}
