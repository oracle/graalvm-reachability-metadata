/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.ImmutableListMultimap;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ImmutableListMultimapTest {
    @Test
    void preservesDuplicateValuesKeyOrderAndImmutability() throws Exception {
        ImmutableListMultimap<String, String> original = ImmutableListMultimap.<String, String>builder()
                .putAll("fruit", "pear", "apple", "pear").put("grain", "rice").build();

        ImmutableListMultimap<String, String> restored = SerializationRoundTrip.copy(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(4, restored.size());
        assertEquals(List.of("fruit", "grain"), List.copyOf(restored.keySet()));
        assertEquals(List.of("pear", "apple", "pear"), restored.get("fruit"));
        assertEquals(List.of(), restored.get("absent"));
        assertEquals(List.of("fruit", "fruit"), restored.inverse().get("pear"));
        assertThrows(UnsupportedOperationException.class, () -> restored.get("fruit").add("plum"));
    }
}
