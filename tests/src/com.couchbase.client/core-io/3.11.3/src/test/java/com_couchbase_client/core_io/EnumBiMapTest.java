/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.collect.EnumBiMap;
import java.time.DayOfWeek;
import java.time.Month;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumBiMapTest {
    @Test
    void preservesContents() throws Exception {
        EnumBiMap<DayOfWeek, Month> original = EnumBiMap.create(DayOfWeek.class, Month.class);
        original.put(DayOfWeek.MONDAY, Month.JANUARY);
        original.put(DayOfWeek.TUESDAY, Month.FEBRUARY);
        EnumBiMap<DayOfWeek, Month> restored = SerializationSupport.roundTrip(original);
        assertEquals(original, restored);
        assertEquals(DayOfWeek.TUESDAY, restored.inverse().get(Month.FEBRUARY));
    }
}
