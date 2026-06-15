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

import org.apache.hadoop.thirdparty.com.google.common.collect.EnumBiMap;
import org.junit.jupiter.api.Test;

public class EnumBiMapTest {
    @Test
    void roundTripsEnumTypesAndEntriesThroughJavaSerialization() throws Exception {
        EnumBiMap<Priority, State> original = EnumBiMap.create(Priority.class, State.class);
        original.put(Priority.LOW, State.QUEUED);
        original.put(Priority.HIGH, State.RUNNING);

        EnumBiMap<Priority, State> restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.keyType()).isSameAs(Priority.class);
        assertThat(restored.valueType()).isSameAs(State.class);
        assertThat(restored.inverse()).containsEntry(State.QUEUED, Priority.LOW)
                .containsEntry(State.RUNNING, Priority.HIGH);
    }

    private static EnumBiMap<Priority, State> roundTrip(EnumBiMap<Priority, State> original)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(original);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(EnumBiMap.class);
            @SuppressWarnings("unchecked")
            EnumBiMap<Priority, State> typedRestored = (EnumBiMap<Priority, State>) restored;
            return typedRestored;
        }
    }

    private enum Priority {
        LOW,
        HIGH
    }

    private enum State {
        QUEUED,
        RUNNING
    }
}
