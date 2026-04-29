/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.hsqldb.lib.ArrayUtil;
import org.junit.jupiter.api.Test;

public class ArrayUtilTest {
    @Test
    public void duplicatesObjectArrayWithSameComponentTypeAndValues() {
        String[] source = {"alpha", "beta", "gamma"};

        String[] duplicate = (String[]) ArrayUtil.duplicateArray(source);

        assertNotSame(source, duplicate);
        assertArrayEquals(source, duplicate);
    }

    @Test
    public void resizesObjectArrayToLargerArrayAndPreservesExistingValues() {
        String[] source = {"alpha", "beta"};

        String[] resized = (String[]) ArrayUtil.resizeArray(source, 4);

        assertNotSame(source, resized);
        assertArrayEquals(new String[]{"alpha", "beta", null, null}, resized);
    }

    @Test
    public void resizeArrayIfDifferentCreatesSmallerArrayOnlyWhenSizeChanges() {
        Integer[] source = {1, 2, 3};

        Integer[] sameSize = (Integer[]) ArrayUtil.resizeArrayIfDifferent(source, source.length);
        Integer[] smaller = (Integer[]) ArrayUtil.resizeArrayIfDifferent(source, 2);

        assertSame(source, sameSize);
        assertNotSame(source, smaller);
        assertArrayEquals(new Integer[]{1, 2}, smaller);
    }

    @Test
    public void toAdjustedArrayInsertsAndRemovesValuesAroundRequestedIndex() {
        String[] source = {"alpha", "gamma", "delta"};

        String[] inserted = (String[]) ArrayUtil.toAdjustedArray(source, "beta", 1, 1);
        String[] removed = (String[]) ArrayUtil.toAdjustedArray(inserted, null, 2, -1);

        assertArrayEquals(new String[]{"alpha", "beta", "gamma", "delta"}, inserted);
        assertArrayEquals(new String[]{"alpha", "beta", "delta"}, removed);
    }
}
