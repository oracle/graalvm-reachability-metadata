/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.TreeMultiset;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;

public class TreeMultisetTest {
    @Test
    void serializesComparatorAndRestoresSortedElementCounts() throws Exception {
        TreeMultiset<String> original = TreeMultiset.create(Collections.reverseOrder());
        original.add("bravo", 2);
        original.add("alpha", 1);
        original.add("charlie", 3);

        TreeMultiset<String> restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.elementSet()).containsExactly("charlie", "bravo", "alpha");
        assertThat(restored.count("alpha")).isEqualTo(1);
        assertThat(restored.count("bravo")).isEqualTo(2);
        assertThat(restored.count("charlie")).isEqualTo(3);
        assertThat(restored.size()).isEqualTo(6);

        restored.add("delta", 4);
        assertThat(restored.elementSet()).containsExactly("delta", "charlie", "bravo", "alpha");
        assertThat(restored.count("delta")).isEqualTo(4);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (T) input.readObject();
        }
    }
}
