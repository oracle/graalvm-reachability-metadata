/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time_joda_time;

import org.joda.time.LocalTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Joda-Time's local-time subsystem for enforcing a customer's daily notification quiet hours.
public class NotificationQuietHoursLocalTimeTest {
    @Test
    void keepsNotificationsInsideTheCustomerQuietHours() {
        LocalTime quietHoursStart = LocalTime.parse("22:30:00");
        LocalTime quietHoursEnd = quietHoursStart.plusHours(8);
        LocalTime notificationTime = LocalTime.parse("23:45:15");
        LocalTime lastQuietMinute = LocalTime.parse("06:29:59");
        LocalTime quietHoursBoundary = LocalTime.parse("06:30:00");

        boolean quietHoursCrossMidnight = quietHoursEnd.isBefore(quietHoursStart);
        boolean notificationIsQuiet = notificationTime.isAfter(quietHoursStart)
                || notificationTime.isBefore(quietHoursEnd);
        boolean lastQuietMinuteIsQuiet = lastQuietMinute.isBefore(quietHoursEnd);

        assertEquals("22:30:00.000", quietHoursStart.toString());
        assertEquals("06:30:00.000", quietHoursEnd.toString());
        assertTrue(quietHoursCrossMidnight);
        assertTrue(notificationIsQuiet);
        assertTrue(lastQuietMinuteIsQuiet);
        assertFalse(quietHoursBoundary.isBefore(quietHoursEnd));
        assertEquals(23 * 60 * 60 * 1000 + 45 * 60 * 1000 + 15 * 1000,
                notificationTime.getMillisOfDay());
        assertEquals("06:30:00.000", quietHoursStart.plusHours(8).toString());
    }
}
