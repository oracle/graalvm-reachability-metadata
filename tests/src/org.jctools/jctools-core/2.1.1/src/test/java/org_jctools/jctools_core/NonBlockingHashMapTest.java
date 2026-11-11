/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.maps.NonBlockingHashMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class NonBlockingHashMapTest {

    @Test
    void serializationRoundTripPreservesMappings() throws Exception {
        NonBlockingHashMap<String, String> map = new NonBlockingHashMap<>();
        map.put("first", "one");
        map.put("second", "two");

        NonBlockingHashMap<String, String> restored = roundTrip(map);

        assertThat(restored).isNotSameAs(map).containsExactlyInAnyOrderEntriesOf(map);
    }

    @SuppressWarnings("unchecked")
    private static NonBlockingHashMap<String, String> roundTrip(
            NonBlockingHashMap<String, String> map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }

        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Object restored = input.readObject();
            assertThat(restored).isInstanceOf(NonBlockingHashMap.class);
            return (NonBlockingHashMap<String, String>) restored;
        }
    }
}
