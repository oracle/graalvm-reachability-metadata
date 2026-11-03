/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time_joda_time;

import org.joda.time.DateTime;
import org.joda.time.DateTimeFieldType;
import org.joda.time.DateTimeZone;
import org.joda.time.DurationFieldType;
import org.joda.time.Partial;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Joda-Time's partial date-time subsystem for recurring maintenance windows without storing a year.
public class RecurringMaintenancePartialTest {
    @Test
    void matchesRecurringMaintenanceWindowAcrossCalendarYears() {
        Partial maintenanceStart = new Partial(DateTimeFieldType.monthOfYear(), 12)
                .with(DateTimeFieldType.dayOfMonth(), 25)
                .with(DateTimeFieldType.hourOfDay(), 22)
                .with(DateTimeFieldType.minuteOfHour(), 0);
        Partial followingWindow = maintenanceStart.withFieldAdded(DurationFieldType.days(), 1);

        DateTime maintenanceIn2025 = new DateTime(2025, 12, 25, 22, 0, DateTimeZone.UTC);
        DateTime maintenanceIn2026 = new DateTime(2026, 12, 25, 22, 0, DateTimeZone.UTC);
        DateTime ordinaryTraffic = new DateTime(2026, 12, 25, 22, 15, DateTimeZone.UTC);

        assertTrue(maintenanceStart.isMatch(maintenanceIn2025));
        assertTrue(maintenanceStart.isMatch(maintenanceIn2026));
        assertFalse(maintenanceStart.isMatch(ordinaryTraffic));
        assertEquals("--12-26T22:00", followingWindow.toString());
        assertEquals("12-25 22:00", maintenanceStart.toString("MM-dd HH:mm"));
    }
}
