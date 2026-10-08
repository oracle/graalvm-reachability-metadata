/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultiset;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class SerializationTest {
    @Test
    void hashBiMapRoundTripSerializesMapEntries() throws Exception {
        HashBiMap<String, String> map = HashBiMap.create();
        map.put("alpha", "one");
        map.put("beta", "two");

        HashBiMap<String, String> restored = roundTrip(map, HashBiMap.class);

        assertThat(restored).isEqualTo(map);
        assertThat(restored.inverse()).isEqualTo(map.inverse());
    }

    @Test
    void hashMultisetRoundTripSerializesElementsAndCounts() throws Exception {
        HashMultiset<String> multiset = HashMultiset.create();
        multiset.add("red", 2);
        multiset.add("blue", 3);

        HashMultiset<String> restored = roundTrip(multiset, HashMultiset.class);

        assertThat(restored).isEqualTo(multiset);
        assertThat(restored.count("red")).isEqualTo(2);
        assertThat(restored.count("blue")).isEqualTo(3);
    }

    @Test
    void arrayListMultimapRoundTripSerializesKeysAndValues() throws Exception {
        ArrayListMultimap<String, String> multimap = ArrayListMultimap.create();
        multimap.put("letters", "a");
        multimap.put("letters", "b");
        multimap.put("numbers", "one");

        ArrayListMultimap<String, String> restored = roundTrip(multimap, ArrayListMultimap.class);

        assertThat(restored).isEqualTo(multimap);
        assertThat(restored.get("letters")).containsExactly("a", "b");
        assertThat(restored.get("numbers")).containsExactly("one");
    }

    private static <T extends Serializable> T roundTrip(T value, Class<?> expectedType)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(expectedType);
            @SuppressWarnings("unchecked")
            T typedRestored = (T) restored;
            return typedRestored;
        }
    }
}
