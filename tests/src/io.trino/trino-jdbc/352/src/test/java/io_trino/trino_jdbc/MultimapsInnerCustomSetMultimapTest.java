/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.base.Supplier;
import io.trino.jdbc.$internal.guava.collect.Multimaps;
import io.trino.jdbc.$internal.guava.collect.SetMultimap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class MultimapsInnerCustomSetMultimapTest {
    @Test
    void restoresSortedKeysAndInsertionOrderedUniqueValues() throws Exception {
        SetMultimap<String, String> original = Multimaps.newSetMultimap(new TreeMap<>(), Sets.INSTANCE);
        original.putAll("z", List.of("pear", "apple", "pear"));
        original.put("a", "carrot");

        SetMultimap<String, String> restored = SerializationRoundTrip.copy(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(List.of("a", "z"), List.copyOf(restored.keySet()));
        assertEquals(List.of("pear", "apple"), List.copyOf(restored.get("z")));
        assertFalse(restored.put("z", "pear"));
        restored.put("new", "plum");
        assertFalse(restored.put("new", "plum"));
        assertEquals(Set.of("plum"), restored.get("new"));
        assertFalse(original.containsKey("new"));
    }

    public enum Sets implements Supplier<Set<String>> {
        INSTANCE;

        @Override
        public Set<String> get() {
            return new LinkedHashSet<>();
        }
    }
}
