/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LinkedHashMultimapTest {
    @Test
    void serializesKeysValuesAndEntryIterationOrder() throws Exception {
        LinkedHashMultimap<String, Integer> original = LinkedHashMultimap.create();
        original.put("second", 20);
        original.put("first", 10);
        original.put("second", 21);
        original.put(null, 0);
        original.put("first", null);

        SetMultimap<String, Integer> restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(entryDescriptions(restored)).containsExactly(
                "second=20",
                "first=10",
                "second=21",
                "null=0",
                "first=null");
        assertThat(restored.asMap().keySet()).containsExactly("second", "first", null);
        assertThat(restored.get("second")).containsExactly(20, 21);
        assertThat(restored.get("first")).containsExactly(10, null);
        assertThat(restored.get(null)).containsExactly(0);
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

    private static List<String> entryDescriptions(SetMultimap<String, Integer> multimap) {
        List<String> descriptions = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : multimap.entries()) {
            descriptions.add(entry.getKey() + "=" + entry.getValue());
        }
        return descriptions;
    }
}
