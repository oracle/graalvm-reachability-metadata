/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.TreeMultiset;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class TreeMultisetTest {
    @Test
    void serializationRoundTripPreservesSortedElementsAndCounts() throws Exception {
        TreeMultiset<String> original = TreeMultiset.create();
        original.add("pear", 2);
        original.add("apple", 3);
        original.add("orange");

        TreeMultiset<String> restored = roundTrip(original);

        assertThat(restored.elementSet()).containsExactly("apple", "orange", "pear");
        assertThat(restored.count("apple")).isEqualTo(3);
        assertThat(restored.count("orange")).isEqualTo(1);
        assertThat(restored.count("pear")).isEqualTo(2);
        assertThat(restored.size()).isEqualTo(6);
    }

    private static <T extends Serializable> T roundTrip(T value)
            throws IOException, ClassNotFoundException {
        byte[] bytes = serialize(value);
        return deserialize(bytes);
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (T) input.readObject();
        }
    }
}
