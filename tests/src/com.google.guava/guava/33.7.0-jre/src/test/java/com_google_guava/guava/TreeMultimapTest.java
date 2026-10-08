/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static java.util.Comparator.reverseOrder;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.TreeMultimap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class TreeMultimapTest {
    @Test
    void roundTripSerializesComparatorsKeysAndValues() throws Exception {
        TreeMultimap<String, String> multimap = TreeMultimap.create(reverseOrder(), reverseOrder());
        multimap.put("letters", "a");
        multimap.put("letters", "c");
        multimap.put("letters", "b");
        multimap.put("numbers", "two");
        multimap.put("numbers", "one");
        multimap.put("letters", "a");

        TreeMultimap<String, String> restored = roundTrip(multimap);

        assertThat(restored).isEqualTo(multimap);
        assertThat(restored.entries())
                .containsExactly(
                        entry("numbers", "two"),
                        entry("numbers", "one"),
                        entry("letters", "c"),
                        entry("letters", "b"),
                        entry("letters", "a"));
        assertThat(restored.keySet()).containsExactly("numbers", "letters");
        assertThat(restored.get("letters")).containsExactly("c", "b", "a");
        assertThat(restored.get("numbers")).containsExactly("two", "one");

        restored.put("greek", "alpha");
        restored.put("greek", "omega");
        assertThat(restored.keySet()).containsExactly("numbers", "letters", "greek");
        assertThat(restored.get("greek")).containsExactly("omega", "alpha");
    }

    private static TreeMultimap<String, String> roundTrip(TreeMultimap<String, String> value)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(TreeMultimap.class);
            @SuppressWarnings("unchecked")
            TreeMultimap<String, String> typedRestored = (TreeMultimap<String, String>) restored;
            return typedRestored;
        }
    }
}
