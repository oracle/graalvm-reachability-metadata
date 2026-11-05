/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.joda.time.DateTimeFieldType;
import io.trino.jdbc.$internal.joda.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class LocalDateInnerPropertyTest {
    @Test
    void restoresMonthPropertyAndImmutableDateArithmetic() throws Exception {
        LocalDate original = new LocalDate(2020, 1, 31);
        LocalDate.Property restored = SerializationRoundTrip.copy(original.monthOfYear());

        assertNotSame(original, restored.getLocalDate());
        assertEquals(original, restored.getLocalDate());
        assertSame(DateTimeFieldType.monthOfYear(), restored.getFieldType());
        assertEquals(1, restored.get());
        assertEquals(new LocalDate(2020, 2, 29), restored.addToCopy(1));
        assertEquals(new LocalDate(2020, 4, 30), restored.setCopy(4));
        assertEquals(original, restored.getLocalDate());
    }
}
