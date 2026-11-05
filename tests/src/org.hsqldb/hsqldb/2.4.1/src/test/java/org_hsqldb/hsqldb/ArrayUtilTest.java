/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import org.hsqldb.lib.ArrayUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ArrayUtilTest {
    @Test
    void copiesResizesAndAdjustsTypedArrays() {
        String[] original = {"alpha", "gamma"};
        String[] copy = (String[]) ArrayUtil.duplicateArray(original);
        assertNotSame(original, copy);
        assertArrayEquals(original, copy);
        assertArrayEquals(new String[]{"alpha", "gamma", null},
                (String[]) ArrayUtil.resizeArray(original, 3));
        assertArrayEquals(new String[]{"alpha"},
                (String[]) ArrayUtil.resizeArrayIfDifferent(original, 1));
        assertSame(original, ArrayUtil.resizeArrayIfDifferent(original, 2));
        String[] inserted = (String[]) ArrayUtil.toAdjustedArray(original, "beta", 1, 1);
        assertArrayEquals(new String[]{"alpha", "beta", "gamma"}, inserted);
        assertArrayEquals(original, (String[]) ArrayUtil.toAdjustedArray(inserted, null, 1, -1));
        assertArrayEquals(new int[]{3, 5, 0}, (int[]) ArrayUtil.resizeArray(new int[]{3, 5}, 3));
    }
}
