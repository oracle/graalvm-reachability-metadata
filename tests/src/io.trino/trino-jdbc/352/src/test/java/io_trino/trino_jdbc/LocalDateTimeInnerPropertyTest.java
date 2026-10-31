/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.joda.time.DateTimeFieldType;
import io.trino.jdbc.$internal.joda.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class LocalDateTimeInnerPropertyTest {
    @Test
    void restoresPropertyAndPreservesTimeDuringDateArithmetic() throws Exception {
        LocalDateTime original = new LocalDateTime(2020, 1, 31, 14, 25, 36, 789);
        LocalDateTime.Property restored = SerializationRoundTrip.copy(original.monthOfYear());

        assertNotSame(original, restored.getLocalDateTime());
        assertEquals(original, restored.getLocalDateTime());
        assertSame(DateTimeFieldType.monthOfYear(), restored.getFieldType());
        assertEquals(1, restored.get());
        assertEquals(new LocalDateTime(2020, 2, 29, 14, 25, 36, 789), restored.addToCopy(1));
        assertEquals(original, restored.getLocalDateTime());
    }
}
