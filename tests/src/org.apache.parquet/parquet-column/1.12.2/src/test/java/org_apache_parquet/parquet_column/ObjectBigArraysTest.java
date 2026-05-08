/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_column;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import shaded.parquet.it.unimi.dsi.fastutil.BigArrays;
import shaded.parquet.it.unimi.dsi.fastutil.objects.ObjectBigArrays;

public class ObjectBigArraysTest {
    @Test
    void newBigArrayCreatesResidualSegmentForTypedPrototype() {
        String[][] array = ObjectBigArrays.newBigArray(new String[0][], BigArrays.SEGMENT_SIZE + 1L);

        try {
            assertEquals(2, array.length);
            assertEquals(BigArrays.SEGMENT_SIZE, array[0].length);
            assertEquals(1, array[1].length);
            assertEquals(BigArrays.SEGMENT_SIZE + 1L, ObjectBigArrays.length(array));
            assertEquals(String[].class, array.getClass().getComponentType());
            assertEquals(String.class, array[0].getClass().getComponentType());
        } finally {
            array = null;
            System.gc();
        }
    }

    @Test
    void newBigArrayCreatesFullSegmentForExactSegmentLength() {
        String[][] array = ObjectBigArrays.newBigArray(new String[0][], BigArrays.SEGMENT_SIZE);

        try {
            assertEquals(1, array.length);
            assertEquals(BigArrays.SEGMENT_SIZE, array[0].length);
            assertEquals(BigArrays.SEGMENT_SIZE, ObjectBigArrays.length(array));
            assertEquals(String[].class, array.getClass().getComponentType());
            assertEquals(String.class, array[0].getClass().getComponentType());
        } finally {
            array = null;
            System.gc();
        }
    }
}
