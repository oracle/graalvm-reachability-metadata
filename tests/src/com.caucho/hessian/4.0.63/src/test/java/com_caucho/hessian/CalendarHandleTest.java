/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import java.util.Calendar;
import java.util.GregorianCalendar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Timeout(120)
public class CalendarHandleTest {
    @Test
    void restoresCalendarSubtypeAndInstant() throws Exception {
        SnapshotCalendar calendar = new SnapshotCalendar();
        calendar.setTimeInMillis(1700000000000L);
        Calendar restored = (Calendar) SerializationSupport.roundTrip(calendar);
        assertInstanceOf(SnapshotCalendar.class, restored);
        assertEquals(calendar.getTimeInMillis(), restored.getTimeInMillis());
    }

    public static class SnapshotCalendar extends GregorianCalendar {
        public SnapshotCalendar() {
            super();
        }
    }
}
