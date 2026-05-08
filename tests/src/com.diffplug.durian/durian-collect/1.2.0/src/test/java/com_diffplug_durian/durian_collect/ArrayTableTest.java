/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_collect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.diffplug.common.collect.ArrayTable;

public class ArrayTableTest {
    @Test
    void toArrayCreatesTypedSnapshotOfTableContents() {
        ArrayTable<String, String, String> table = ArrayTable.create(
                Arrays.asList("row-one", "row-two"),
                Arrays.asList("column-one", "column-two", "column-three"));
        table.put("row-one", "column-one", "alpha");
        table.set(0, 2, "gamma");
        table.put("row-two", "column-two", "beta");

        String[][] snapshot = table.toArray(String.class);

        assertEquals(String[][].class, snapshot.getClass());
        assertEquals(2, snapshot.length);
        assertEquals(3, snapshot[0].length);
        assertEquals(3, snapshot[1].length);
        assertArrayEquals(new String[] {"alpha", null, "gamma"}, snapshot[0]);
        assertArrayEquals(new String[] {null, "beta", null}, snapshot[1]);

        snapshot[0][0] = "changed";
        table.set(1, 1, "updated");

        assertEquals("alpha", table.at(0, 0));
        assertEquals("beta", snapshot[1][1]);
    }
}
