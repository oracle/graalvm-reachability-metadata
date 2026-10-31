/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.base.Supplier;
import io.trino.jdbc.$internal.guava.collect.Multimaps;
import io.trino.jdbc.$internal.guava.collect.SortedSetMultimap;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultimapsInnerCustomSortedSetMultimapTest {
    @Test
    void restoresCaseInsensitiveComparatorAndFactory() throws Exception {
        SortedSetMultimap<String, String> original = Multimaps.newSortedSetMultimap(
                new TreeMap<>(), SortedSets.INSTANCE);
        original.putAll("fruit", List.of("pear", "Apple", "PEAR"));

        SortedSetMultimap<String, String> restored = SerializationRoundTrip.copy(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(List.of("Apple", "pear"), List.copyOf(restored.get("fruit")));
        assertEquals(0, restored.valueComparator().compare("a", "A"));
        assertFalse(restored.put("fruit", "APPLE"));
        restored.putAll("new", List.of("Zulu", "alpha", "ALPHA"));
        assertEquals(List.of("alpha", "Zulu"), List.copyOf(restored.get("new")));
        assertTrue(original.get("new").isEmpty());
    }

    public enum SortedSets implements Supplier<SortedSet<String>> {
        INSTANCE;

        @Override
        public SortedSet<String> get() {
            return new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        }
    }
}
