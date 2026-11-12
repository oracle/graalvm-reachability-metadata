/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP;

import com.zaxxer.hikari.util.FastList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FastListTest {
    @Test
    public void growsDefaultCapacityAndPreservesElementOrder() {
        FastList<String> values = new FastList<>(String.class);
        for (int i = 0; i < 40; i++) {
            assertTrue(values.add("entry-" + i));
        }

        assertEquals(40, values.size());
        for (int i = 0; i < values.size(); i++) {
            assertEquals("entry-" + i, values.get(i));
        }
        assertEquals("entry-39", values.removeLast());
        assertEquals(39, values.size());
    }

    @Test
    public void growsExplicitCapacityAndSupportsRemovalAndReuse() {
        FastList<String> values = new FastList<>(String.class, 1);
        values.add("first");
        values.add("second");
        values.add("third");

        assertEquals("second", values.remove(1));
        assertEquals(2, values.size());
        assertEquals("third", values.get(1));
        assertEquals("first", values.set(0, "replacement"));
        assertEquals("replacement", values.get(0));
        values.clear();
        assertTrue(values.isEmpty());
        values.add("reused");
        assertEquals("reused", values.removeLast());
        assertTrue(values.isEmpty());
    }
}
