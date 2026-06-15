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

import org.apache.hadoop.thirdparty.com.google.common.collect.EnumMultiset;
import org.junit.jupiter.api.Test;

public class EnumMultisetTest {
    @Test
    void roundTripsEnumTypeAndCountsThroughJavaSerialization() throws Exception {
        EnumMultiset<Priority> original = EnumMultiset.create(Priority.class);
        original.add(Priority.LOW, 3);
        original.add(Priority.HIGH, 2);
        original.remove(Priority.LOW);

        EnumMultiset<Priority> restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.count(Priority.LOW)).isEqualTo(2);
        assertThat(restored.count(Priority.HIGH)).isEqualTo(2);
        assertThat(restored.count(Priority.MEDIUM)).isZero();

        restored.add(Priority.MEDIUM, 4);
        assertThat(restored.count(Priority.MEDIUM)).isEqualTo(4);
        assertThat(restored.elementSet())
                .containsExactly(Priority.LOW, Priority.MEDIUM, Priority.HIGH);
    }

    private static EnumMultiset<Priority> roundTrip(EnumMultiset<Priority> original)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(original);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(EnumMultiset.class);
            @SuppressWarnings("unchecked")
            EnumMultiset<Priority> typedRestored = (EnumMultiset<Priority>) restored;
            return typedRestored;
        }
    }

    private enum Priority {
        LOW,
        MEDIUM,
        HIGH
    }
}
