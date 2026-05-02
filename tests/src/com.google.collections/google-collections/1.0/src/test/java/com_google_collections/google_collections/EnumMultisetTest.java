/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.EnumMultiset;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class EnumMultisetTest {
    @Test
    void serializedEnumMultisetPreservesEnumBackingTypeAndCounts() throws Exception {
        EnumMultiset<Priority> original = EnumMultiset.create(Priority.class);
        original.add(Priority.LOW);
        original.add(Priority.MEDIUM, 2);
        original.add(Priority.HIGH, 3);

        EnumMultiset<Priority> restored = deserialize(serialize(original));

        assertThat(restored.count(Priority.LOW)).isEqualTo(1);
        assertThat(restored.count(Priority.MEDIUM)).isEqualTo(2);
        assertThat(restored.count(Priority.HIGH)).isEqualTo(3);
        assertThat(restored.size()).isEqualTo(6);
        assertThat(restored.elementSet()).containsExactly(Priority.LOW, Priority.MEDIUM, Priority.HIGH);

        restored.add(Priority.LOW, 4);
        assertThat(restored.count(Priority.LOW)).isEqualTo(5);
    }

    private static byte[] serialize(EnumMultiset<Priority> value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static EnumMultiset<Priority> deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (EnumMultiset<Priority>) input.readObject();
        }
    }

    private enum Priority {
        LOW,
        MEDIUM,
        HIGH
    }
}
