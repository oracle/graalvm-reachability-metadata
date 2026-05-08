/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_collect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.diffplug.common.collect.ObjectArrays;

public class ObjectArraysTest {
    @Test
    void newArrayCreatesArrayWithRequestedComponentTypeAndLength() {
        String[] strings = ObjectArrays.newArray(String.class, 3);

        assertEquals(String[].class, strings.getClass());
        assertEquals(String.class, strings.getClass().getComponentType());
        assertEquals(3, strings.length);

        strings[0] = "alpha";
        strings[1] = "beta";
        strings[2] = "gamma";
        assertArrayEquals(new String[] {"alpha", "beta", "gamma"}, strings);
    }
}
