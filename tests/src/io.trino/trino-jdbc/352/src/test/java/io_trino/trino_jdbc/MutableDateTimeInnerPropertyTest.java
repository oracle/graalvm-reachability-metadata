/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.joda.time.DateTimeFieldType;
import io.trino.jdbc.$internal.joda.time.DateTimeZone;
import io.trino.jdbc.$internal.joda.time.MutableDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class MutableDateTimeInnerPropertyTest {
    @Test
    void restoresPropertyBoundToAnIndependentMutableInstant() throws Exception {
        MutableDateTime original = new MutableDateTime(2020, 1, 31, 14, 25, 36, 789, DateTimeZone.UTC);
        MutableDateTime.Property restored = SerializationRoundTrip.copy(original.monthOfYear());

        assertNotSame(original, restored.getMutableDateTime());
        assertEquals(original, restored.getMutableDateTime());
        assertSame(DateTimeFieldType.monthOfYear(), restored.getFieldType());
        assertEquals(1, restored.get());
        assertSame(restored.getMutableDateTime(), restored.add(1));
        assertEquals(new MutableDateTime(2020, 2, 29, 14, 25, 36, 789, DateTimeZone.UTC),
                restored.getMutableDateTime());
        restored.set(4);
        assertEquals(4, restored.getMutableDateTime().getMonthOfYear());
        assertEquals(1, original.getMonthOfYear());
    }
}
