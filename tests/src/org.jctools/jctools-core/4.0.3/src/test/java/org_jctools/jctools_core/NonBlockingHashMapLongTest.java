/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.jctools.maps.NonBlockingHashMapLong;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NonBlockingHashMapLongTest {

    @Test
    void serializationRoundTripPreservesPrimitiveLongEntries() throws IOException, ClassNotFoundException {
        NonBlockingHashMapLong<String> original = new NonBlockingHashMapLong<>();
        original.put(0L, "zero");
        original.put(42L, "forty-two");

        NonBlockingHashMapLong<String> restored = roundTrip(original);

        assertThat(restored).hasSize(2);
        assertThat(restored.get(0L)).isEqualTo("zero");
        assertThat(restored.get(42L)).isEqualTo("forty-two");
        assertThat(restored.put(7L, "seven")).isNull();
        assertThat(restored.get(7L)).isEqualTo("seven");
    }

    private static <V> NonBlockingHashMapLong<V> roundTrip(NonBlockingHashMapLong<V> map)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            @SuppressWarnings("unchecked")
            NonBlockingHashMapLong<V> restored = (NonBlockingHashMapLong<V>) input.readObject();
            return restored;
        }
    }
}
