/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.collect.BiMap;
import com.couchbase.client.core.deps.com.google.common.collect.EnumHashBiMap;
import java.time.DayOfWeek;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AbstractBiMapInnerInverseTest {
    @Test
    void preservesContents() throws Exception {
        EnumHashBiMap<DayOfWeek, String> original = EnumHashBiMap.create(DayOfWeek.class);
        original.put(DayOfWeek.MONDAY, "first");
        BiMap<String, DayOfWeek> restored = SerializationSupport.roundTrip(original.inverse());
        assertEquals(DayOfWeek.MONDAY, restored.get("first"));
        assertSame(restored, restored.inverse().inverse());
        restored.put("second", DayOfWeek.TUESDAY);
        assertEquals("second", restored.inverse().get(DayOfWeek.TUESDAY));
    }
}
