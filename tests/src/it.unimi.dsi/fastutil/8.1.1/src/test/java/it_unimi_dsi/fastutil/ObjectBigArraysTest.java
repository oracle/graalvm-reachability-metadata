/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

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
