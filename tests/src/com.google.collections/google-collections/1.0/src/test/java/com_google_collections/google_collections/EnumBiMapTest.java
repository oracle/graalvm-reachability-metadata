/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.EnumBiMap;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class EnumBiMapTest {
    @Test
    void serializationRoundTripPreservesEnumTypesAndMappings() throws Exception {
        EnumBiMap<Priority, Status> map = EnumBiMap.create(Priority.class, Status.class);
        map.put(Priority.LOW, Status.QUEUED);
        map.put(Priority.HIGH, Status.RUNNING);

        EnumBiMap<Priority, Status> restored = roundTrip(map);

        assertThat(restored.keyType()).isEqualTo(Priority.class);
        assertThat(restored.valueType()).isEqualTo(Status.class);
        assertThat(restored)
                .containsEntry(Priority.LOW, Status.QUEUED)
                .containsEntry(Priority.HIGH, Status.RUNNING)
                .hasSize(2);
        assertThat(restored.inverse())
                .containsEntry(Status.QUEUED, Priority.LOW)
                .containsEntry(Status.RUNNING, Priority.HIGH);
    }

    @SuppressWarnings("unchecked")
    private static <K extends Enum<K>, V extends Enum<V>> EnumBiMap<K, V> roundTrip(
            EnumBiMap<K, V> map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (EnumBiMap<K, V>) input.readObject();
        }
    }

    private enum Priority {
        LOW,
        HIGH
    }

    private enum Status {
        QUEUED,
        RUNNING
    }
}
