/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import org.eclipse.jetty.util.ArrayUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ArrayUtilTest {
    @Test
    void createsTypedArraysWhenAppendingToAnEmptyArray() {
        String[] explicit = ArrayUtil.addToArray(null, "first", String.class);
        String[] inferred = ArrayUtil.addToArray(null, "first", null);
        assertArrayEquals(new String[]{"first"}, explicit);
        assertArrayEquals(explicit, inferred);
        assertEquals(String[].class, explicit.getClass());
        assertEquals(String[].class, inferred.getClass());
        assertArrayEquals(new String[]{"first", "second"}, ArrayUtil.addToArray(explicit, "second", String.class));
    }

    @Test
    void prependsToEmptyAndPopulatedArrays() {
        String[] empty = ArrayUtil.prependToArray("last", null, String.class);
        assertArrayEquals(new String[]{"last"}, empty);
        assertEquals(String[].class, empty.getClass());
        String[] populated = ArrayUtil.prependToArray("first", empty, String.class);
        assertArrayEquals(new String[]{"first", "last"}, populated);
        assertEquals(String[].class, populated.getClass());
        assertArrayEquals(new String[]{"last"}, empty);
    }

    @Test
    void removesAnElementWhilePreservingTheArrayTypeAndOrder() {
        String[] original = {"first", "middle", "last"};
        String[] result = ArrayUtil.removeFromArray(original, "middle");
        assertArrayEquals(new String[]{"first", "last"}, result);
        assertEquals(String[].class, result.getClass());
        assertArrayEquals(new String[]{"first", "middle", "last"}, original);
        assertSame(original, ArrayUtil.removeFromArray(original, "absent"));
    }
}
