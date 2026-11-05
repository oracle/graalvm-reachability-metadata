/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.joda.time.DateMidnight;
import io.trino.jdbc.$internal.joda.time.DateTimeFieldType;
import io.trino.jdbc.$internal.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

@SuppressWarnings("deprecation")
public class DateMidnightInnerPropertyTest {
    @Test
    void restoresMonthPropertyWithItsDateAndChronology() throws Exception {
        DateMidnight original = new DateMidnight(2020, 1, 31, DateTimeZone.UTC);
        DateMidnight.Property restored = SerializationRoundTrip.copy(original.monthOfYear());

        assertNotSame(original, restored.getDateMidnight());
        assertEquals(original, restored.getDateMidnight());
        assertSame(DateTimeFieldType.monthOfYear(), restored.getFieldType());
        assertEquals(1, restored.get());
        assertEquals(new DateMidnight(2020, 2, 29, DateTimeZone.UTC), restored.addToCopy(1));
        assertEquals(1, original.getMonthOfYear());
        assertEquals(original, restored.getDateMidnight());
    }
}
