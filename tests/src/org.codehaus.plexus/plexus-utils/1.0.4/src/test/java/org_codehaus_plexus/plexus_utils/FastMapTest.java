/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.plexus.util.FastMap;
import org.junit.jupiter.api.Test;

public class FastMapTest {
    @Test
    void serializesEntriesWithInsertionOrderAndCapacity() throws Exception {
        FastMap map = new FastMap(4);
        map.put("first-key", "first-value");
        map.put("second-key", "second-value");

        FastMap restored = roundTrip(map);

        assertThat(restored).isNotSameAs(map);
        assertThat(restored.capacity()).isEqualTo(map.capacity());
        assertThat(restored).containsEntry("first-key", "first-value");
        assertThat(restored).containsEntry("second-key", "second-value");
        assertThat(restored).hasSize(2);
        assertThat(keysInIterationOrder(restored)).containsExactly("first-key", "second-key");
    }

    private static FastMap roundTrip(FastMap map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(map);
        }

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (FastMap) in.readObject();
        }
    }

    private static List<Object> keysInIterationOrder(FastMap map) {
        List<Object> keys = new ArrayList<>();
        keys.addAll(map.keySet());
        return keys;
    }
}
