/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.ObjectArrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ObjectArraysTest {
    @Test
    void resizesAndCopiesTypedArrays() {
        String[] original = {"north", "east", "south"};
        String[] expanded = ObjectArrays.ensureCapacity(original, 5);
        assertEquals(String[].class, expanded.getClass());
        assertArrayEquals(new String[]{"north", "east", "south", null, null}, expanded);
        assertSame(expanded, ObjectArrays.ensureCapacity(expanded, 4));

        String[] partial = ObjectArrays.ensureCapacity(original, 4, 1);
        assertArrayEquals(new String[]{"north", null, null, null}, partial);
        String[] grown = ObjectArrays.grow(original, 7);
        assertEquals(7, grown.length);
        assertArrayEquals(original, ObjectArrays.trim(grown, original.length));
        assertArrayEquals(new String[]{"east", "south"}, ObjectArrays.copy(original, 1, 2));
        assertArrayEquals(new String[]{"north", "east", "south"}, original);
    }
}
