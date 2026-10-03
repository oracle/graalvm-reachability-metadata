/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.LinkedHashMultimap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class LinkedHashMultimapTest {
    @Test
    void roundTripSerializesKeysValuesAndIterationOrder() throws Exception {
        LinkedHashMultimap<String, String> multimap = LinkedHashMultimap.create();
        multimap.put("letters", "a");
        multimap.put("numbers", "one");
        multimap.put("letters", "b");
        multimap.put("numbers", "two");
        multimap.put("letters", "a");

        LinkedHashMultimap<String, String> restored = roundTrip(multimap);

        assertThat(restored).isEqualTo(multimap);
        assertThat(restored.keySet()).containsExactly("letters", "numbers");
        assertThat(restored.entries())
                .containsExactly(
                        entry("letters", "a"),
                        entry("numbers", "one"),
                        entry("letters", "b"),
                        entry("numbers", "two"));
        assertThat(restored.get("letters")).containsExactly("a", "b");
        assertThat(restored.get("numbers")).containsExactly("one", "two");
    }

    private static LinkedHashMultimap<String, String> roundTrip(
            LinkedHashMultimap<String, String> value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(LinkedHashMultimap.class);
            @SuppressWarnings("unchecked")
            LinkedHashMultimap<String, String> typedRestored =
                    (LinkedHashMultimap<String, String>) restored;
            return typedRestored;
        }
    }
}
