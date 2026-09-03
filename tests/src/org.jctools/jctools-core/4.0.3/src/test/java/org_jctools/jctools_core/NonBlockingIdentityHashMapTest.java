/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.maps.NonBlockingIdentityHashMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class NonBlockingIdentityHashMapTest {

    @Test
    void serializationRoundTripPreservesDistinctIdentityKeys() throws Exception {
        String firstKey = new String("shared-key");
        String secondKey = new String("shared-key");
        NonBlockingIdentityHashMap<String, String> map = new NonBlockingIdentityHashMap<>();
        map.put(firstKey, "first");
        map.put(secondKey, "second");

        NonBlockingIdentityHashMap<String, String> restored = roundTrip(map);
        List<String> restoredKeys = new ArrayList<>(restored.keySet());

        assertThat(restored).isNotSameAs(map).hasSize(2);
        assertThat(restored.values()).containsExactlyInAnyOrder("first", "second");
        assertThat(restoredKeys).hasSize(2);
        assertThat(restoredKeys.get(0)).isEqualTo(restoredKeys.get(1));
        assertThat(restoredKeys.get(0)).isNotSameAs(restoredKeys.get(1));
        assertThat(restored.get(new String("shared-key"))).isNull();
    }

    @SuppressWarnings("unchecked")
    private static NonBlockingIdentityHashMap<String, String> roundTrip(
            NonBlockingIdentityHashMap<String, String> map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }

        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Object restored = input.readObject();
            assertThat(restored).isInstanceOf(NonBlockingIdentityHashMap.class);
            return (NonBlockingIdentityHashMap<String, String>) restored;
        }
    }
}
