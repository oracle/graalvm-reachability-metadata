/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.LinkedListMultimap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LinkedListMultimapTest {
    @Test
    void serializesEntriesAndRestoresGlobalAndPerKeyOrder() throws Exception {
        LinkedListMultimap<String, Integer> original = LinkedListMultimap.create();
        original.put("first", 1);
        original.put("second", 20);
        original.put("first", 2);
        original.put(null, 0);
        original.put("second", null);

        LinkedListMultimap<String, Integer> copy = roundTrip(original);

        assertThat(copy).isEqualTo(original);
        assertThat(entryDescriptions(copy)).containsExactly(
                "first=1",
                "second=20",
                "first=2",
                "null=0",
                "second=null");
        assertThat(copy.get("first")).containsExactly(1, 2);
        assertThat(copy.get("second")).containsExactly(20, null);
        assertThat(copy.get(null)).containsExactly(0);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream input = new ObjectInputStream(inputBytes)) {
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
