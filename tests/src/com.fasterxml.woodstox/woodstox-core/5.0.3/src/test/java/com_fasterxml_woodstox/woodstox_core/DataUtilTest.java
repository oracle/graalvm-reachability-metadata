/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import com.ctc.wstx.util.DataUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class DataUtilTest {
    @Test
    void growsReferenceArrayByHalfAndPreservesContents() {
        String[] original = {"root", "child", "text", "end"};

        String[] grown = (String[]) DataUtil.growArrayBy50Pct(original);

        assertNotSame(original, grown);
        assertArrayEquals(new String[]{"root", "child", "text", "end", null, null}, grown);
        grown[0] = "replacement";
        assertArrayEquals(new String[]{"root", "child", "text", "end"}, original);
    }

    @Test
    void growsPrimitiveArrayByHalfAndZeroInitializesNewSlots() {
        int[] original = {10, 20, 30, 40};

        int[] grown = (int[]) DataUtil.growArrayBy50Pct(original);

        assertNotSame(original, grown);
        assertArrayEquals(new int[]{10, 20, 30, 40, 0, 0}, grown);
        grown[0] = 99;
        assertArrayEquals(new int[]{10, 20, 30, 40}, original);
    }

    @Test
    void growsReferenceArrayToRequestedMinimum() {
        String[] original = {"first", "second"};

        String[] grown = (String[]) DataUtil.growArrayToAtLeast(original, 5);

        assertNotSame(original, grown);
        assertArrayEquals(new String[]{"first", "second", null, null, null}, grown);
        grown[0] = "replacement";
        assertArrayEquals(new String[]{"first", "second"}, original);
    }

    @Test
    void growsPrimitiveArrayProportionallyWhenMinimumIsSmaller() {
        int[] original = {10, 20, 30};

        int[] grown = (int[]) DataUtil.growArrayToAtLeast(original, 4);

        assertNotSame(original, grown);
        assertArrayEquals(new int[]{10, 20, 30, 0, 0}, grown);
        grown[0] = 99;
        assertArrayEquals(new int[]{10, 20, 30}, original);
    }
}
