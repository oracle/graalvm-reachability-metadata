/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.maps.NonBlockingHashMapLong;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class NonBlockingHashMapLongTest {

    @Test
    void serializationRoundTripPreservesLongKeyMappings() throws Exception {
        NonBlockingHashMapLong<String> map = new NonBlockingHashMapLong<>();
        map.put(0L, "zero");
        map.put(42L, "positive");
        map.put(-7L, "negative");

        NonBlockingHashMapLong<String> restored = roundTrip(map);

        assertThat(restored).isNotSameAs(map).containsExactlyInAnyOrderEntriesOf(map);
        assertThat(restored.get(0L)).isEqualTo("zero");
    }

    @SuppressWarnings("unchecked")
    private static NonBlockingHashMapLong<String> roundTrip(
            NonBlockingHashMapLong<String> map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }

        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Object restored = input.readObject();
            assertThat(restored).isInstanceOf(NonBlockingHashMapLong.class);
            return (NonBlockingHashMapLong<String>) restored;
        }
    }
}
