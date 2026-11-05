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

public class HsqlArrayListTest {
    @Test
    void growsAndExportsTypedContents() {
        HsqlArrayList list = new HsqlArrayList(1);
        list.add("first");
        list.add("second");
        list.add(1, "middle");
        list.trim();
        assertEquals(3, list.size());
        assertArrayEquals(new Object[]{"first", "middle", "second"}, list.toArray());
        assertArrayEquals(new Object[]{"middle", "second"}, list.toArray(1, 3));
        assertArrayEquals(new String[]{"first", "middle", "second"}, (String[]) list.toArray(new String[0]));
        assertEquals("middle", list.remove(1));
        assertArrayEquals(new Object[]{"first", "second"}, list.toArray());
    }
}
