/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.EnumBiMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class EnumBiMapTest {
    @Test
    void roundTripSerializesEnumKeyAndValueTypes() throws Exception {
        EnumBiMap<Priority, Status> map = EnumBiMap.create(Priority.class, Status.class);
        map.put(Priority.LOW, Status.QUEUED);
        map.put(Priority.HIGH, Status.RUNNING);

        EnumBiMap<Priority, Status> restored = roundTrip(map);

        assertThat(restored).isEqualTo(map);
        assertThat(restored.keyType()).isSameAs(Priority.class);
        assertThat(restored.valueType()).isSameAs(Status.class);
        assertThat(restored.inverse().get(Status.QUEUED)).isSameAs(Priority.LOW);
        assertThat(restored.inverse().get(Status.RUNNING)).isSameAs(Priority.HIGH);
    }

    private static EnumBiMap<Priority, Status> roundTrip(EnumBiMap<Priority, Status> map)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(EnumBiMap.class);
            @SuppressWarnings("unchecked")
            EnumBiMap<Priority, Status> typedRestored = (EnumBiMap<Priority, Status>) restored;
            return typedRestored;
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
