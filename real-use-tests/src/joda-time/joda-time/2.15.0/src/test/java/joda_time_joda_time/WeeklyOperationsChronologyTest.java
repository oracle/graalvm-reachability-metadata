/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time_joda_time;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.chrono.ISOChronology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Joda-Time's chronology subsystem for assigning operations to ISO weeks at a year boundary.
public class WeeklyOperationsChronologyTest {
    private static final ISOChronology ISO_UTC = ISOChronology.getInstanceUTC();

    @Test
    void assignsAYearBoundaryOperationToTheCorrectIsoWeekAndBounds() {
        DateTime operation = new DateTime(2025, 12, 31, 9, 30, 0, ISO_UTC);
        DateTime weekStart = operation.withDayOfWeek(DateTimeConstants.MONDAY).withTimeAtStartOfDay();
        DateTime weekEnd = weekStart.plusWeeks(1).minusMillis(1);

        assertEquals(2026, operation.getWeekyear());
        assertEquals(1, operation.getWeekOfWeekyear());
        assertEquals(DateTimeConstants.WEDNESDAY, operation.getDayOfWeek());
        assertEquals(new DateTime(2025, 12, 29, 0, 0, 0, ISO_UTC), weekStart);
        assertEquals(new DateTime(2026, 1, 4, 23, 59, 59, 999, ISO_UTC), weekEnd);
    }
}
