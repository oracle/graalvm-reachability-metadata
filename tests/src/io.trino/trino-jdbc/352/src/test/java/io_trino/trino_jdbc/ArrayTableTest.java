/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.ArrayTable;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArrayTableTest {
    @Test
    void exportsIndependentTypedMatrix() {
        ArrayTable<String, String, Integer> table = ArrayTable.create(List.of("r1", "r2"), List.of("c1", "c2"));
        table.put("r1", "c1", 11);
        table.put("r2", "c2", 22);
        Integer[][] matrix = table.toArray(Integer.class);
        assertArrayEquals(new Integer[] {11, null}, matrix[0]);
        assertArrayEquals(new Integer[] {null, 22}, matrix[1]);
        matrix[0][0] = 99;
        assertEquals(11, table.get("r1", "c1"));
    }
}
