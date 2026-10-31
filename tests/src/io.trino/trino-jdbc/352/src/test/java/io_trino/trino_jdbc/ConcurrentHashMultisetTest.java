/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.ConcurrentHashMultiset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConcurrentHashMultisetTest {
    @Test
    void restoresCountsAndAtomicUpdates() throws Exception {
        ConcurrentHashMultiset<String> original = ConcurrentHashMultiset.create();
        original.add("queued", 4);
        original.add("done", 2);

        ConcurrentHashMultiset<String> restored = SerializationRoundTrip.copy(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(6, restored.size());
        assertTrue(restored.setCount("queued", 4, 7));
        assertFalse(restored.setCount("queued", 4, 9));
        assertTrue(restored.removeExactly("done", 2));
        assertEquals(0, restored.count("done"));
        restored.add("new", 3);
        assertEquals(10, restored.size());
        assertEquals(4, original.count("queued"));
        assertEquals(2, original.count("done"));
        assertEquals(0, original.count("new"));
    }
}
