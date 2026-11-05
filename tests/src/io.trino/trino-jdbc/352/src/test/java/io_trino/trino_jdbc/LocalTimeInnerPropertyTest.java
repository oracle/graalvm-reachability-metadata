/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.joda.time.DateTimeFieldType;
import io.trino.jdbc.$internal.joda.time.LocalTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class LocalTimeInnerPropertyTest {
    @Test
    void restoresHourPropertyAndWrapsAtMidnight() throws Exception {
        LocalTime original = new LocalTime(23, 45, 12, 345);
        LocalTime.Property restored = SerializationRoundTrip.copy(original.hourOfDay());

        assertNotSame(original, restored.getLocalTime());
        assertEquals(original, restored.getLocalTime());
        assertSame(DateTimeFieldType.hourOfDay(), restored.getFieldType());
        assertEquals(23, restored.get());
        assertEquals(new LocalTime(1, 45, 12, 345), restored.addCopy(2));
        assertEquals(new LocalTime(8, 45, 12, 345), restored.setCopy(8));
        assertEquals(original, restored.getLocalTime());
    }
}
