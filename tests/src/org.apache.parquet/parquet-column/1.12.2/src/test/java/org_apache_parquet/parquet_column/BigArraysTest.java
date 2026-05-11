/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_column;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import shaded.parquet.it.unimi.dsi.fastutil.BigArrays;

public class BigArraysTest {
    @Test
    void wrapObjectArrayCreatesSingleSegmentBigArray() {
        String[] values = new String[] {"alpha", "bravo"};

        String[][] wrapped = BigArrays.wrap(values);

        assertEquals(1, wrapped.length);
        assertSame(values, wrapped[0]);
        assertEquals("alpha", BigArrays.get(wrapped, 0));
        assertEquals("bravo", BigArrays.get(wrapped, 1));
    }

    @Test
    void forceCapacityObjectArrayAllocatesFullAndResidualSegments() {
        String[][] grown = BigArrays.forceCapacity(new String[0][], BigArrays.SEGMENT_SIZE + 1L, 0L);

        try {
            assertEquals(2, grown.length);
            assertEquals(BigArrays.SEGMENT_SIZE, grown[0].length);
            assertEquals(1, grown[1].length);
            assertEquals(BigArrays.SEGMENT_SIZE + 1L, BigArrays.length(grown));
        } finally {
            grown = null;
            System.gc();
        }
    }

    @Test
    void forceCapacityObjectArrayAllocatesFullSegmentForExactSegmentLength() {
        String[][] grown = BigArrays.forceCapacity(new String[0][], BigArrays.SEGMENT_SIZE, 0L);

        try {
            assertEquals(1, grown.length);
            assertEquals(BigArrays.SEGMENT_SIZE, grown[0].length);
            assertEquals(BigArrays.SEGMENT_SIZE, BigArrays.length(grown));
        } finally {
            grown = null;
            System.gc();
        }
    }
}
