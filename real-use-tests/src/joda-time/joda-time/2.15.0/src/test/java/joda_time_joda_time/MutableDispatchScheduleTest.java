/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time_joda_time;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.MutableDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Joda-Time's mutable date-time subsystem for adjusting a dispatch schedule and handing immutable snapshots to other services.
public class MutableDispatchScheduleTest {
    private static final DateTimeZone UTC = DateTimeZone.UTC;

    @Test
    void adjustsDispatchScheduleWithoutChangingTheOriginalSnapshot() {
        MutableDateTime dispatch = new MutableDateTime(2025, 7, 14, 8, 45, 0, 0, UTC);
        DateTime receivedAt = dispatch.toDateTime();

        dispatch.addHours(2);
        dispatch.minuteOfHour().set(0);
        DateTime dispatchWindowStart = dispatch.toDateTime();

        dispatch.addDays(1);
        DateTime nextDispatch = dispatch.toDateTime();

        assertEquals(new DateTime(2025, 7, 14, 8, 45, UTC), receivedAt);
        assertEquals(new DateTime(2025, 7, 14, 10, 0, UTC), dispatchWindowStart);
        assertEquals(new DateTime(2025, 7, 15, 10, 0, UTC), nextDispatch);
        assertEquals(2025, dispatch.getYear());
        assertEquals(15, dispatch.getDayOfMonth());
        assertEquals(10, dispatch.getHourOfDay());
        assertEquals(0, dispatch.getMinuteOfHour());
    }
}
