/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import org.hsqldb.lib.HsqlArrayList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class HsqlArrayListTest {
    @Test
    void toArrayCreatesSnapshotUsingBackingArrayComponentType() {
        HsqlArrayList list = new HsqlArrayList(new String[] {"alpha", "beta", null, null}, 2);

        Object[] snapshot = list.toArray();

        assertInstanceOf(String[].class, snapshot);
        assertArrayEquals(new String[] {"alpha", "beta"}, snapshot);
        assertNotSame(list.getArray(), snapshot);
    }

    @Test
    void toArrayRangeCreatesComponentTypedSlice() {
        HsqlArrayList list = new HsqlArrayList(new String[] {"zero", "one", "two", "three"}, 4);

        Object[] slice = list.toArray(1, 3);

        assertInstanceOf(String[].class, slice);
        assertArrayEquals(new String[] {"one", "two"}, slice);
    }

    @Test
    void toArrayWithTooSmallDestinationAllocatesComponentTypedDestination() {
        HsqlArrayList list = new HsqlArrayList();

        list.add("north");
        list.add("south");

        String[] destination = new String[1];

        Object copied = list.toArray(destination);

        assertInstanceOf(String[].class, copied);
        assertNotSame(destination, copied);
        assertArrayEquals(new String[] {"north", "south"}, (String[]) copied);
    }

    @Test
    void toArrayWithLargeEnoughDestinationReusesDestination() {
        HsqlArrayList list = new HsqlArrayList();

        list.add("east");
        list.add("west");

        String[] destination = new String[] {null, null, "preserved"};

        Object copied = list.toArray(destination);

        assertSame(destination, copied);
        assertArrayEquals(new String[] {"east", "west", "preserved"}, destination);
        assertEquals(2, list.size());
    }
}
