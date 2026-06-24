/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class AbstractBiMapInnerInverseTest {
    @Test
    void inverseSerializationRoundTripPreservesMappingsAndInverseRelationship() throws Exception {
        HashBiMap<String, Integer> forward = HashBiMap.create();
        forward.put("one", 1);
        forward.put("two", 2);
        BiMap<Integer, String> inverse = forward.inverse();

        BiMap<Integer, String> restored = roundTrip(inverse);

        assertThat(restored)
                .containsEntry(1, "one")
                .containsEntry(2, "two")
                .hasSize(2);
        assertThat(restored.inverse())
                .containsEntry("one", 1)
                .containsEntry("two", 2)
                .hasSize(2);
        assertThat(restored.inverse().inverse()).isSameAs(restored);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
