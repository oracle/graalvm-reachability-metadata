/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.core.util.CyclicBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CyclicBufferTest {
    @Test
    void keepsNewestElementsInInsertionOrderAndCanBeReused() {
        CyclicBuffer<String> buffer = new CyclicBuffer<>(String.class, 2);
        buffer.add("old");
        buffer.add("second");
        buffer.add("third");
        assertArrayEquals(new String[]{"second", "third"}, buffer.removeAll());
        assertTrue(buffer.isEmpty());
        buffer.add("new");
        assertArrayEquals(new String[]{"new"}, buffer.removeAll());
        assertArrayEquals(new String[0], buffer.removeAll());
    }
}
