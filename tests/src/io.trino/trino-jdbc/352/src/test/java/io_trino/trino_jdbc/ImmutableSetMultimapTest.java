/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.ImmutableSetMultimap;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ImmutableSetMultimapTest {
    @Test
    void preservesUniqueValuesAndComparatorAcrossSerialization() throws Exception {
        ImmutableSetMultimap<String, String> original = ImmutableSetMultimap.<String, String>builder()
                .orderValuesBy(Comparator.reverseOrder())
                .putAll("fruit", "apple", "pear", "apple")
                .put("vegetable", "carrot").build();

        ImmutableSetMultimap<String, String> restored = SerializationRoundTrip.copy(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(3, restored.size());
        assertEquals(List.of("pear", "apple"), List.copyOf(restored.get("fruit")));
        assertEquals(Set.of(), restored.get("absent"));
        assertEquals(Set.of("fruit"), restored.inverse().get("pear"));
        assertThrows(UnsupportedOperationException.class, () -> restored.get("fruit").add("plum"));
    }

    @Test
    void preservesUnsortedSetValues() throws Exception {
        ImmutableSetMultimap<String, String> restored = SerializationRoundTrip.copy(
                ImmutableSetMultimap.of("color", "blue", "color", "green"));
        assertEquals(Set.of("blue", "green"), restored.get("color"));
        assertEquals(2, restored.size());
    }
}
