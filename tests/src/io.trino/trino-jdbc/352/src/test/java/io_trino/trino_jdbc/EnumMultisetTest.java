/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.EnumMultiset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnumMultisetTest {
    public enum Priority {
        LOW, NORMAL, HIGH
    }

    @Test
    void preservesCountsEnumOrderAndIndependentUpdates() throws Exception {
        EnumMultiset<Priority> original = EnumMultiset.create(Priority.class);
        original.add(Priority.HIGH, 2);
        original.add(Priority.LOW, 3);

        EnumMultiset<Priority> restored = SerializationRoundTrip.copy(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(List.of(Priority.LOW, Priority.HIGH), List.copyOf(restored.elementSet()));
        assertEquals(5, restored.size());
        assertEquals(3, restored.remove(Priority.LOW, 2));
        restored.add(Priority.NORMAL, 4);
        assertEquals(1, restored.count(Priority.LOW));
        assertEquals(4, restored.count(Priority.NORMAL));
        assertEquals(3, original.count(Priority.LOW));
        assertEquals(0, original.count(Priority.NORMAL));
    }

    @Test
    void preservesEnumTypeForAnEmptyMultiset() throws Exception {
        EnumMultiset<Priority> restored = SerializationRoundTrip.copy(EnumMultiset.create(Priority.class));
        assertTrue(restored.isEmpty());
        restored.add(Priority.NORMAL, 2);
        assertEquals(2, restored.count(Priority.NORMAL));
    }
}
