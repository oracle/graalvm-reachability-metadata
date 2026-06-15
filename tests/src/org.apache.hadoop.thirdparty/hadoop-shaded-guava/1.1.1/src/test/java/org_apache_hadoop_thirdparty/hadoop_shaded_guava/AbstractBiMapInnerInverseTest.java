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

import org.apache.hadoop.thirdparty.com.google.common.collect.BiMap;
import org.apache.hadoop.thirdparty.com.google.common.collect.EnumBiMap;
import org.junit.jupiter.api.Test;

public class AbstractBiMapInnerInverseTest {
    @Test
    void inverseRoundTripSerializesForwardBiMap() throws Exception {
        EnumBiMap<Priority, Status> forward = EnumBiMap.create(Priority.class, Status.class);
        forward.put(Priority.LOW, Status.QUEUED);
        forward.put(Priority.HIGH, Status.RUNNING);

        BiMap<Status, Priority> restoredInverse = roundTrip(forward.inverse());

        assertThat(restoredInverse).isEqualTo(forward.inverse());
        assertThat(restoredInverse.get(Status.QUEUED)).isSameAs(Priority.LOW);
        assertThat(restoredInverse.get(Status.RUNNING)).isSameAs(Priority.HIGH);
        assertThat(restoredInverse.inverse()).isInstanceOf(EnumBiMap.class);
        assertThat(restoredInverse.inverse()).isEqualTo(forward);
        assertThat(restoredInverse.inverse().inverse()).isSameAs(restoredInverse);
    }

    private static <K, V> BiMap<K, V> roundTrip(BiMap<K, V> value)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(BiMap.class);
            @SuppressWarnings("unchecked")
            BiMap<K, V> typedRestored = (BiMap<K, V>) restored;
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
