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
import org.jctools.maps.NonBlockingIdentityHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NonBlockingIdentityHashMapTest {

    @Test
    void serializationRoundTripPreservesIdentityEntries() throws IOException, ClassNotFoundException {
        NonBlockingIdentityHashMap<String, String> original = new NonBlockingIdentityHashMap<>();
        original.put(new String("key"), "first");
        original.put(new String("key"), "second");

        NonBlockingIdentityHashMap<String, String> restored = roundTrip(original);

        assertThat(restored).hasSize(2);
        assertThat(restored.values()).containsExactlyInAnyOrder("first", "second");
        assertThat(restored.keySet()).allMatch("key"::equals);
    }

    private static <K, V> NonBlockingIdentityHashMap<K, V> roundTrip(NonBlockingIdentityHashMap<K, V> map)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            @SuppressWarnings("unchecked")
            NonBlockingIdentityHashMap<K, V> restored = (NonBlockingIdentityHashMap<K, V>) input.readObject();
            return restored;
        }
    }
}
