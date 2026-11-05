/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.collect.ArrayTable;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ArrayTableTest {
    @Test
    void exercisesPublicApi() throws Exception {
        ArrayTable<String, String, Integer> table = ArrayTable.create(List.of("north", "south"), List.of("red", "blue"));
        table.put("north", "blue", 7);
        table.put("south", "red", 9);
        Integer[][] cells = table.toArray(Integer.class);
        assertNull(cells[0][0]);
        assertEquals(7, cells[0][1]);
        assertEquals(9, cells[1][0]);
        cells[0][1] = 100;
        assertEquals(7, table.get("north", "blue"));
    }
}
