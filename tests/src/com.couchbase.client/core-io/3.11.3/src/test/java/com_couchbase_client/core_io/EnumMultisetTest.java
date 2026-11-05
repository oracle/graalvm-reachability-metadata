/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.collect.EnumMultiset;
import java.time.DayOfWeek;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumMultisetTest {
    @Test
    void preservesContents() throws Exception {
        EnumMultiset<DayOfWeek> original = EnumMultiset.create(DayOfWeek.class);
        original.add(DayOfWeek.MONDAY, 3);
        EnumMultiset<DayOfWeek> restored = SerializationSupport.roundTrip(original);
        assertEquals(3, restored.count(DayOfWeek.MONDAY));
        restored.add(DayOfWeek.TUESDAY, 2);
        assertEquals(5, restored.size());
    }
}
