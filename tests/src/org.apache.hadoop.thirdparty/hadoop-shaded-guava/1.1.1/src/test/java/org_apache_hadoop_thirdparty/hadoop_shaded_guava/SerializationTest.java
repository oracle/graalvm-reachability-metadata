/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.hadoop.thirdparty.com.google.common.collect.HashBiMap;
import org.apache.hadoop.thirdparty.com.google.common.collect.HashMultimap;
import org.apache.hadoop.thirdparty.com.google.common.collect.HashMultiset;
import org.apache.hadoop.thirdparty.com.google.common.collect.Multiset;
import org.apache.hadoop.thirdparty.com.google.common.collect.TreeMultiset;
import org.junit.jupiter.api.Test;

public class SerializationTest {
    @Test
    void roundTripsHashBiMapUsingSerializedMapEntries() throws Exception {
        HashBiMap<String, Integer> original = HashBiMap.create();
        original.put("one", 1);
        original.put("two", 2);

        @SuppressWarnings("unchecked")
        HashBiMap<String, Integer> restored = (HashBiMap<String, Integer>) roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.inverse().get(2)).isEqualTo("two");
    }

    @Test
    void roundTripsHashMultimapUsingSerializedKeysAndValues() throws Exception {
        HashMultimap<String, String> original = HashMultimap.create();
        original.put("colors", "red");
        original.put("colors", "blue");
        original.put("shapes", "square");

        @SuppressWarnings("unchecked")
        HashMultimap<String, String> restored = (HashMultimap<String, String>) roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.get("colors")).containsExactlyInAnyOrder("red", "blue");
    }

    @Test
    void roundTripsHashMultisetUsingSerializedElementsAndCounts() throws Exception {
        HashMultiset<String> original = HashMultiset.create();
        original.add("apple", 3);
        original.add("pear", 2);

        @SuppressWarnings("unchecked")
        HashMultiset<String> restored = (HashMultiset<String>) roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.count("apple")).isEqualTo(3);
    }

    @Test
    void roundTripsTreeMultisetUsingSerializedFieldSetters() throws Exception {
        TreeMultiset<String> original = TreeMultiset.create();
        original.add("alpha", 2);
        original.add("beta", 1);

        @SuppressWarnings("unchecked")
        TreeMultiset<String> restored = (TreeMultiset<String>) roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.elementSet()).containsExactly("alpha", "beta");
        assertThat(restored.entrySet())
                .extracting(Multiset.Entry::getCount)
                .containsExactly(2, 1);
    }

    private static Object roundTrip(Object value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            return inputStream.readObject();
        }
    }
}
