/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.apache.pulsar.shade.com.google.common.collect.ArrayListMultimap;
import org.apache.pulsar.shade.com.google.common.collect.HashBiMap;
import org.apache.pulsar.shade.com.google.common.collect.HashMultiset;
import org.junit.jupiter.api.Test;

public class SerializationTest {
    @Test
    void preservesMapMultisetAndMultimapEntries() throws Exception {
        HashBiMap<String, Integer> map = HashBiMap.create();
        map.put("one", 1);
        map.put("two", 2);
        HashMultiset<String> multiset = HashMultiset.create();
        multiset.add("red", 2);
        multiset.add("blue", 3);
        ArrayListMultimap<String, String> multimap = ArrayListMultimap.create();
        multimap.put("letters", "a");
        multimap.put("letters", "b");

        HashBiMap<String, Integer> restoredMap = roundTrip(map);
        HashMultiset<String> restoredMultiset = roundTrip(multiset);
        ArrayListMultimap<String, String> restoredMultimap = roundTrip(multimap);

        assertThat(restoredMap).isEqualTo(map);
        assertThat(restoredMap.inverse().get(2)).isEqualTo("two");
        assertThat(restoredMultiset.count("red")).isEqualTo(2);
        assertThat(restoredMultiset.count("blue")).isEqualTo(3);
        assertThat(restoredMultimap.get("letters")).containsExactly("a", "b");
    }

    private static <T extends Serializable> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            @SuppressWarnings("unchecked")
            T restored = (T) input.readObject();
            return restored;
        }
    }
}
