/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.sundr.deps.org.apache.commons.collections.IteratorUtils;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

public class IteratorUtilsTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Iterator iterator = IteratorUtils.getIterator(new Items());
        assertEquals("one", iterator.next());
        assertEquals("two", iterator.next());
        assertFalse(iterator.hasNext());
        assertArrayEquals(new String[]{"one", "two"}, IteratorUtils.toArray(List.of("one", "two").iterator(), String.class));
    }

    public static class Items {
        public Iterator<String> iterator() {
            return List.of("one", "two").iterator();
        }
    }
}
