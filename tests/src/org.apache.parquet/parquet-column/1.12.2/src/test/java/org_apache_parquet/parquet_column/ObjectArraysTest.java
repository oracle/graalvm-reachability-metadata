/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_column;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

import shaded.parquet.it.unimi.dsi.fastutil.objects.ObjectArrays;

public class ObjectArraysTest {
    @Test
    void copyPreservesComponentTypeForTypedArrays() {
        String[] values = {"zero", "one", "two", "three"};

        String[] copy = ObjectArrays.copy(values, 1, 2);

        assertArrayEquals(new String[] {"one", "two"}, copy);
        assertEquals(String[].class, copy.getClass());
        assertEquals(String.class, copy.getClass().getComponentType());
        assertNotSame(values, copy);
    }
}
