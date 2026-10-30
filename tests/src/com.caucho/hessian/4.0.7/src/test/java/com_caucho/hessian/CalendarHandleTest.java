/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import org.junit.jupiter.api.Test;

import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CalendarHandleTest {
    @Test
    void restoresApplicationCalendarSubtypeAndInstant() throws Exception {
        BusinessCalendar original = new BusinessCalendar();
        original.setTimeInMillis(1700000000123L);
        BusinessCalendar restored = HessianStreams.roundTrip(original);
        assertEquals(BusinessCalendar.class, restored.getClass());
        assertEquals(original.getTimeInMillis(), restored.getTimeInMillis());
    }

    public static class BusinessCalendar extends GregorianCalendar {
    }
}
