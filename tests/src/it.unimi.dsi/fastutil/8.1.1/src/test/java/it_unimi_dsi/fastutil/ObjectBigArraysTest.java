/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.BigArrays;
import it.unimi.dsi.fastutil.objects.ObjectBigArrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ObjectBigArraysTest {
    @Test
    void wrapsAndGrowsTypedSegments() {
        String[] values = {"north", "east", "south"};
        String[][] wrapped = ObjectBigArrays.wrap(values);
        assertEquals(String[][].class, wrapped.getClass());
        assertSame(values, wrapped[0]);
        assertEquals(3L, ObjectBigArrays.length(wrapped));

        String[][] grown = ObjectBigArrays.ensureCapacity(wrapped, 6);
        assertEquals(String[][].class, grown.getClass());
        assertEquals(6L, ObjectBigArrays.length(grown));
        assertArrayEquals(new String[]{"north", "east", "south", null, null, null}, grown[0]);
        ObjectBigArrays.set(grown, 5, "last");
        assertEquals("last", ObjectBigArrays.get(grown, 5));
        assertSame(grown, ObjectBigArrays.ensureCapacity(grown, 4));

        String[][] partial = ObjectBigArrays.ensureCapacity(wrapped, 5, 1);
        assertEquals("north", ObjectBigArrays.get(partial, 0));
        assertNull(ObjectBigArrays.get(partial, 1));
        assertArrayEquals(new String[]{"north", "east", "south"}, values);
    }

    @Test
    void allocatesACompleteTypedSegment() {
        String[][] allocated = ObjectBigArrays.newBigArray(new String[0][], BigArrays.SEGMENT_SIZE);

        assertEquals(String[][].class, allocated.getClass());
        assertEquals(1, allocated.length);
        assertEquals(String[].class, allocated[0].getClass());
        assertEquals(BigArrays.SEGMENT_SIZE, allocated[0].length);
        assertEquals((long) BigArrays.SEGMENT_SIZE, ObjectBigArrays.length(allocated));
        assertNull(ObjectBigArrays.get(allocated, 0));
        assertNull(ObjectBigArrays.get(allocated, BigArrays.SEGMENT_SIZE - 1L));
        ObjectBigArrays.set(allocated, BigArrays.SEGMENT_SIZE - 1L, "segment end");
        assertEquals("segment end", ObjectBigArrays.get(allocated, BigArrays.SEGMENT_SIZE - 1L));
    }

    @Test
    void allocatesTypedSegmentsAcrossABoundary() {
        long length = BigArrays.SEGMENT_SIZE + 2L;
        String[][] allocated = ObjectBigArrays.newBigArray(new String[0][], length);

        assertEquals(String[][].class, allocated.getClass());
        assertEquals(2, allocated.length);
        assertEquals(String[].class, allocated[0].getClass());
        assertEquals(BigArrays.SEGMENT_SIZE, allocated[0].length);
        assertEquals(2, allocated[1].length);
        assertEquals(length, ObjectBigArrays.length(allocated));
        assertNull(ObjectBigArrays.get(allocated, BigArrays.SEGMENT_SIZE - 1L));
        assertNull(ObjectBigArrays.get(allocated, BigArrays.SEGMENT_SIZE));
        ObjectBigArrays.set(allocated, BigArrays.SEGMENT_SIZE - 1L, "before");
        ObjectBigArrays.set(allocated, BigArrays.SEGMENT_SIZE, "after");
        assertEquals("before", allocated[0][BigArrays.SEGMENT_SIZE - 1]);
        assertEquals("after", allocated[1][0]);
        assertNull(ObjectBigArrays.get(allocated, length - 1));
    }

    @Test
    void expandsTypedArrayToACompleteSegment() {
        String[][] original = {{"first", "second"}};
        String[][] expanded = ObjectBigArrays.ensureCapacity(original, BigArrays.SEGMENT_SIZE);

        assertEquals(String[][].class, expanded.getClass());
        assertEquals(1, expanded.length);
        assertEquals(String[].class, expanded[0].getClass());
        assertEquals(BigArrays.SEGMENT_SIZE, expanded[0].length);
        assertEquals((long) BigArrays.SEGMENT_SIZE, ObjectBigArrays.length(expanded));
        assertEquals("first", ObjectBigArrays.get(expanded, 0));
        assertEquals("second", ObjectBigArrays.get(expanded, 1));
        assertNull(ObjectBigArrays.get(expanded, BigArrays.SEGMENT_SIZE - 1L));
        ObjectBigArrays.set(expanded, BigArrays.SEGMENT_SIZE - 1L, "last");
        assertEquals("last", ObjectBigArrays.get(expanded, BigArrays.SEGMENT_SIZE - 1L));
        assertArrayEquals(new String[]{"first", "second"}, original[0]);
    }

    @Test
    void expandsTypedArrayAcrossASegmentBoundary() {
        String[][] original = {{"retained", "discarded"}};
        long length = BigArrays.SEGMENT_SIZE + 2L;
        String[][] expanded = ObjectBigArrays.ensureCapacity(original, length, 1);

        assertEquals(String[][].class, expanded.getClass());
        assertEquals(2, expanded.length);
        assertEquals(String[].class, expanded[0].getClass());
        assertEquals(BigArrays.SEGMENT_SIZE, expanded[0].length);
        assertEquals(2, expanded[1].length);
        assertEquals(length, ObjectBigArrays.length(expanded));
        assertEquals("retained", ObjectBigArrays.get(expanded, 0));
        assertNull(ObjectBigArrays.get(expanded, 1));
        ObjectBigArrays.set(expanded, BigArrays.SEGMENT_SIZE - 1L, "before");
        ObjectBigArrays.set(expanded, BigArrays.SEGMENT_SIZE, "after");
        assertEquals("before", expanded[0][BigArrays.SEGMENT_SIZE - 1]);
        assertEquals("after", expanded[1][0]);
        assertNull(ObjectBigArrays.get(expanded, length - 1));
        assertArrayEquals(new String[]{"retained", "discarded"}, original[0]);
    }

    @Test
    void allocatesTypedBigArraysFromPrototypes() {
        String[][] prototype = new String[0][];
        String[][] empty = ObjectBigArrays.newBigArray(prototype, 0);
        assertEquals(String[][].class, empty.getClass());
        assertEquals(0L, ObjectBigArrays.length(empty));

        String[][] allocated = ObjectBigArrays.newBigArray(prototype, 4);
        assertEquals(String[][].class, allocated.getClass());
        assertEquals(4L, ObjectBigArrays.length(allocated));
        ObjectBigArrays.set(allocated, 2, "center");
        assertEquals("center", ObjectBigArrays.get(allocated, 2));
        assertNull(ObjectBigArrays.get(allocated, 3));
    }
}
