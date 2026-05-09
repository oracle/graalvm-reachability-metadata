/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_osgi_bundle;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.HashBiMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

public class SerializationTest {
    @Test
    void hashBiMapSerializesAndDeserializesEntries() throws IOException, ClassNotFoundException {
        HashBiMap<String, String> map = HashBiMap.create();
        map.put("left", "right");
        map.put("up", "down");

        HashBiMap<String, String> deserialized = roundTrip(map);

        assertThat(deserialized).containsEntry("left", "right");
        assertThat(deserialized).containsEntry("up", "down");
        assertThat(deserialized.inverse()).containsEntry("right", "left");
    }

    @Test
    void arrayListMultimapSerializesAndDeserializesKeysAndValues() throws IOException, ClassNotFoundException {
        ArrayListMultimap<String, String> multimap = ArrayListMultimap.create();
        multimap.put("letters", "a");
        multimap.put("letters", "b");
        multimap.put("numbers", "one");

        ArrayListMultimap<String, String> deserialized = roundTrip(multimap);

        assertThat(deserialized.get("letters")).containsExactly("a", "b");
        assertThat(deserialized.get("numbers")).containsExactly("one");
    }

    @Test
    void concurrentHashMultisetRestoresFieldSettersAndElementCounts() throws IOException, ClassNotFoundException {
        ConcurrentHashMultiset<String> multiset = ConcurrentHashMultiset.create();
        multiset.add("alpha", 2);
        multiset.add("beta", 3);

        ConcurrentHashMultiset<String> deserialized = roundTrip(multiset);

        assertThat(deserialized.count("alpha")).isEqualTo(2);
        assertThat(deserialized.count("beta")).isEqualTo(3);
        assertThat(deserialized.size()).isEqualTo(5);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T object) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            outputStream.writeObject(object);
        }

        byte[] serialized = byteArrayOutputStream.toByteArray();
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (T) inputStream.readObject();
        }
    }
}
