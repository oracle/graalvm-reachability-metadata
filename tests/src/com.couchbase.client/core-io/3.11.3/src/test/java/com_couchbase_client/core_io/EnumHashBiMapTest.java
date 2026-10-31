/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.collect.EnumHashBiMap;
import java.time.DayOfWeek;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumHashBiMapTest {
    @Test
    void preservesContents() throws Exception {
        EnumHashBiMap<DayOfWeek, String> original = EnumHashBiMap.create(DayOfWeek.class);
        original.put(DayOfWeek.MONDAY, "first");
        original.put(DayOfWeek.TUESDAY, "second");
        EnumHashBiMap<DayOfWeek, String> restored = SerializationSupport.roundTrip(original);
        assertEquals(original, restored);
        assertEquals(DayOfWeek.TUESDAY, restored.inverse().get("second"));
    }
}
