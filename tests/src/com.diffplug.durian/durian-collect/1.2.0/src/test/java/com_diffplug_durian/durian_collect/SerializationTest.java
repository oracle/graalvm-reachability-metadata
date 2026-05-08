/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.junit.jupiter.api.Test;

import com.diffplug.common.collect.ArrayListMultimap;
import com.diffplug.common.collect.HashBiMap;
import com.diffplug.common.collect.Multimap;
import com.diffplug.common.collect.TreeMultiset;

public class SerializationTest {
    @Test
    void serializesHashBiMapEntries() throws Exception {
        HashBiMap<String, String> original = HashBiMap.create();
        original.put("first", "one");
        original.put("second", "two");

        HashBiMap<String, String> copy = roundTrip(original, HashBiMap.class);

        assertThat(copy).containsEntry("first", "one").containsEntry("second", "two");
        assertThat(copy.inverse()).containsEntry("one", "first").containsEntry("two", "second");
    }

    @Test
    void serializesTreeMultisetCounts() throws Exception {
        TreeMultiset<String> original = TreeMultiset.create();
        original.add("alpha", 2);
        original.add("beta", 3);

        TreeMultiset<String> copy = roundTrip(original, TreeMultiset.class);

        assertThat(copy.count("alpha")).isEqualTo(2);
        assertThat(copy.count("beta")).isEqualTo(3);
        assertThat(copy.elementSet()).containsExactly("alpha", "beta");
    }

    @Test
    void serializesArrayListMultimapKeysAndValues() throws Exception {
        ArrayListMultimap<String, String> original = ArrayListMultimap.create();
        original.put("letters", "a");
        original.put("letters", "b");
        original.put("digits", "1");

        Multimap<String, String> copy = roundTrip(original, ArrayListMultimap.class);

        assertThat(copy.get("letters")).containsExactly("a", "b");
        assertThat(copy.get("digits")).containsExactly("1");
        assertThat(copy.asMap()).hasSize(2);
    }

    private static <T extends Serializable> T roundTrip(T original, Class<?> expectedType)
            throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
            output.flush();
            serialized = bytes.toByteArray();
        }

        Object copy;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            copy = input.readObject();
        }

        assertThat(copy).isInstanceOf(expectedType);
        @SuppressWarnings("unchecked")
        T typedCopy = (T) copy;
        return typedCopy;
    }
}
