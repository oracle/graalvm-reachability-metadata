/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_bundles_repackaged.jersey_guava;

import static org.assertj.core.api.Assertions.assertThat;

import jersey.repackaged.com.google.common.collect.TreeMultimap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

public class TreeMultimapTest {
    @Test
    void serializationRoundTripPreservesSortedKeysValuesAndComparators() throws Exception {
        TreeMultimap<String, String> original = TreeMultimap.create();
        original.put("bravo", "two");
        original.put("alpha", "three");
        original.put("alpha", "one");
        original.put("charlie", "one");

        TreeMultimap<String, String> deserialized = roundTrip(original);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.keySet()).containsExactly("alpha", "bravo", "charlie");
        assertThat(deserialized.get("alpha")).containsExactly("one", "three");
        assertThat(deserialized.keyComparator().compare("alpha", "bravo")).isLessThan(0);
        assertThat(deserialized.valueComparator().compare("one", "two")).isLessThan(0);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T original) throws Exception {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (T) input.readObject();
        }
    }
}
