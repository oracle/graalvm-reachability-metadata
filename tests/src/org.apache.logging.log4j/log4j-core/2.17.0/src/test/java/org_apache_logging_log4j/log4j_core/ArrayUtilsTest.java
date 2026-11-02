/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.core.util.ArrayUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ArrayUtilsTest {
    @Test
    void removesElementsWithoutChangingTheOriginalArray() {
        String[] original = {"first", "middle", "last"};
        String[] result = ArrayUtils.remove(original, 1);
        assertArrayEquals(new String[]{"first", "last"}, result);
        assertArrayEquals(new String[]{"first", "middle", "last"}, original);
        assertArrayEquals(new String[0], ArrayUtils.remove(new String[]{"only"}, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> ArrayUtils.remove(original, 3));
    }
}
