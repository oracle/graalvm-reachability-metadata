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

public class SerializationInnerFieldSetterTest {
    @Test
    void restoresImmutableMultimapBackingMapAndSize() throws Exception {
        ImmutableListMultimap<String, Integer> original = ImmutableListMultimap.<String, Integer>builder()
                .putAll("odd", 1, 3, 5).putAll("even", 2, 4).build();

        ImmutableListMultimap<String, Integer> restored = SerializationRoundTrip.copy(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(5, restored.size());
        assertEquals(List.of(1, 3, 5), restored.get("odd"));
        assertEquals(List.of(2, 4), restored.get("even"));
        assertEquals(List.of("odd"), restored.inverse().get(3));
    }
}
