/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.util.EnumLookupTable;
import java.time.DayOfWeek;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class EnumLookupTableTest {
    @Test
    void exercisesPublicApi() throws Exception {
        EnumLookupTable<DayOfWeek> table = EnumLookupTable.create(DayOfWeek.class, day -> day.getValue() * 2);
        assertEquals(DayOfWeek.FRIDAY, table.get(10));
        assertNull(table.get(3));
        assertEquals(DayOfWeek.MONDAY, table.getOrDefault(100, DayOfWeek.MONDAY));
    }
}
