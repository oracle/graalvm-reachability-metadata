/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.LinkedListMultimap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class LinkedListMultimapTest {
    @Test
    void roundTripSerializesEntriesAndRestoresGlobalAndPerKeyOrder() throws Exception {
        LinkedListMultimap<String, Integer> multimap = LinkedListMultimap.create();
        multimap.put("first", 1);
        multimap.put("second", 20);
        multimap.put("first", 2);
        multimap.put(null, 0);
        multimap.put("second", null);

        LinkedListMultimap<String, Integer> restored = roundTrip(multimap);

        assertThat(restored).isEqualTo(multimap);
        assertThat(entryDescriptions(restored))
                .containsExactly(
                        "first=1", "second=20", "first=2", "null=0", "second=null");
        assertThat(restored.get("first")).containsExactly(1, 2);
        assertThat(restored.get("second")).containsExactly(20, null);
        assertThat(restored.get(null)).containsExactly(0);
    }

    private static LinkedListMultimap<String, Integer> roundTrip(
            LinkedListMultimap<String, Integer> value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(LinkedListMultimap.class);
            @SuppressWarnings("unchecked")
            LinkedListMultimap<String, Integer> typedRestored =
                    (LinkedListMultimap<String, Integer>) restored;
            return typedRestored;
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
