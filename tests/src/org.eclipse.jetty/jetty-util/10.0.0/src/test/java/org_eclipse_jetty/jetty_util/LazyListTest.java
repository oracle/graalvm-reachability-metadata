/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import org.eclipse.jetty.util.LazyList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LazyListTest {
    @Test
    void convertsEmptySingletonAndMultipleEntriesToTypedArrays() {
        Object values = null;
        String[] empty = (String[]) LazyList.toArray(values, String.class);
        assertArrayEquals(new String[0], empty);
        values = LazyList.add(values, "first");
        String[] singleton = (String[]) LazyList.toArray(values, String.class);
        assertArrayEquals(new String[]{"first"}, singleton);
        values = LazyList.add(values, "second");
        String[] multiple = (String[]) LazyList.toArray(values, String.class);
        assertArrayEquals(new String[]{"first", "second"}, multiple);
        assertEquals(String[].class, multiple.getClass());
    }

    @Test
    void convertsBoxedListEntriesToAPrimitiveArray() {
        Object values = LazyList.add(null, 7);
        values = LazyList.add(values, 11);
        int[] numbers = (int[]) LazyList.toArray(values, int.class);
        assertArrayEquals(new int[]{7, 11}, numbers);
        assertArrayEquals(new int[0], (int[]) LazyList.toArray(null, int.class));
    }
}
