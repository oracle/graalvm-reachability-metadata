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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMultiset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class SerializationTest {
    @Test
    void serializesHashBiMapEntries() throws Exception {
        HashBiMap<String, Integer> original = HashBiMap.create();
        original.put("one", 1);
        original.put("two", 2);

        HashBiMap<String, Integer> copy = roundTrip(original);

        assertThat(copy).isEqualTo(original);
        assertThat(copy.inverse().get(1)).isEqualTo("one");
        assertThat(copy.inverse().get(2)).isEqualTo("two");
    }

    @Test
    void serializesHashMultisetElementsAndCounts() throws Exception {
        HashMultiset<String> original = HashMultiset.create();
        original.add("apples", 3);
        original.add("oranges", 2);

        HashMultiset<String> copy = roundTrip(original);

        assertThat(copy.count("apples")).isEqualTo(3);
        assertThat(copy.count("oranges")).isEqualTo(2);
        assertThat(copy.size()).isEqualTo(5);
    }

    @Test
    void serializesArrayListMultimapKeysAndValues() throws Exception {
        ArrayListMultimap<String, Integer> original = ArrayListMultimap.create();
        original.put("first", 1);
        original.put("first", 2);
        original.put("second", 3);

        ArrayListMultimap<String, Integer> copy = roundTrip(original);

        assertThat(copy.get("first")).containsExactly(1, 2);
        assertThat(copy.get("second")).containsExactly(3);
        assertThat(copy.size()).isEqualTo(3);
    }

    @Test
    void deserializesImmutableMultisetFinalFields() throws Exception {
        ImmutableMultiset<String> original = ImmutableMultiset.of("red", "blue", "red");

        ImmutableMultiset<String> copy = roundTrip(original);

        assertThat(copy.count("red")).isEqualTo(2);
        assertThat(copy.count("blue")).isEqualTo(1);
        assertThat(copy.size()).isEqualTo(3);
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
}
