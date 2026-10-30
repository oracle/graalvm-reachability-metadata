/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_woodstox.woodstox_core_asl;

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
        grown[0] = "changed";
        assertArrayEquals(new String[]{"root", "child", "text", "end"}, original);
    }

    @Test
    void growsPrimitiveArrayByHalfAndInitializesExtraSlots() {
        int[] original = {7, 11, 13, 17};

        int[] grown = (int[]) DataUtil.growArrayBy50Pct(original);

        assertNotSame(original, grown);
        assertArrayEquals(new int[]{7, 11, 13, 17, 0, 0}, grown);
        assertArrayEquals(new int[]{7, 11, 13, 17}, original);
    }

    @Test
    void growsReferenceArrayToRequestedMinimum() {
        String[] original = {"element", "attribute"};

        String[] grown = (String[]) DataUtil.growArrayToAtLeast(original, 5);

        assertNotSame(original, grown);
        assertArrayEquals(new String[]{"element", "attribute", null, null, null}, grown);
        grown[0] = "replacement";
        assertArrayEquals(new String[]{"element", "attribute"}, original);
    }

    @Test
    void growsPrimitiveArrayByRoundedHalfWhenMinimumIsSmaller() {
        int[] original = {2, 3, 5};

        int[] grown = (int[]) DataUtil.growArrayToAtLeast(original, 4);

        assertNotSame(original, grown);
        assertArrayEquals(new int[]{2, 3, 5, 0, 0}, grown);
        assertArrayEquals(new int[]{2, 3, 5}, original);
    }
}
