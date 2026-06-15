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

import org.apache.hadoop.thirdparty.com.google.common.collect.EnumHashBiMap;
import org.junit.jupiter.api.Test;

public class EnumHashBiMapTest {
    @Test
    void roundTripsKeyTypeAndEntriesThroughJavaSerialization() throws Exception {
        EnumHashBiMap<Priority, String> original = EnumHashBiMap.create(Priority.class);
        original.put(Priority.LOW, "queued");
        original.put(Priority.HIGH, "running");

        EnumHashBiMap<Priority, String> restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.keyType()).isSameAs(Priority.class);
        assertThat(restored.inverse()).containsEntry("queued", Priority.LOW)
                .containsEntry("running", Priority.HIGH);
    }

    private static EnumHashBiMap<Priority, String> roundTrip(EnumHashBiMap<Priority, String> original)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(original);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(EnumHashBiMap.class);
            @SuppressWarnings("unchecked")
            EnumHashBiMap<Priority, String> typedRestored = (EnumHashBiMap<Priority, String>) restored;
            return typedRestored;
        }
    }

    private enum Priority {
        LOW,
        HIGH
    }
}
