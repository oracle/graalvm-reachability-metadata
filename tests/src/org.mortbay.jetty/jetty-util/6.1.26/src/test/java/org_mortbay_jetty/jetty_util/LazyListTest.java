/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import org.junit.jupiter.api.Test;
import org.mortbay.util.LazyList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class LazyListTest {
    @Test
    public void convertsEmptySingletonAndMultipleValuesToTypedArrays() {
        assertArrayEquals(new String[0], (String[]) LazyList.toArray(null, String.class));
        Object names = LazyList.add(null, "alpha");
        assertArrayEquals(new String[]{"alpha"}, (String[]) LazyList.toArray(names, String.class));
        names = LazyList.add(names, "beta");
        assertArrayEquals(new String[]{"alpha", "beta"}, (String[]) LazyList.toArray(names, String.class));
        Object numbers = LazyList.add(null, 3);
        numbers = LazyList.add(numbers, 7);
        assertArrayEquals(new int[]{3, 7}, (int[]) LazyList.toArray(numbers, int.class));
    }

    @Test
    public void growsAndShrinksArraysWithoutLosingTheirComponentType() {
        String[] names = (String[]) LazyList.addToArray(null, "alpha", String.class);
        assertArrayEquals(new String[]{"alpha"}, names);
        names = (String[]) LazyList.addToArray(names, "beta", String.class);
        assertArrayEquals(new String[]{"alpha", "beta"}, names);
        names = (String[]) LazyList.removeFromArray(names, "alpha");
        assertArrayEquals(new String[]{"beta"}, names);
        assertArrayEquals(new String[0], (String[]) LazyList.removeFromArray(names, "beta"));
    }
}
