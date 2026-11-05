/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time_joda_time;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Joda-Time's time-zone conversion subsystem for recurring customer appointments across daylight-saving changes.
public class CustomerAppointmentTimeZoneTest {
    private static final DateTimeZone NEW_YORK = DateTimeZone.forID("America/New_York");
    private static final DateTimeFormatter APPOINTMENT_DISPLAY = DateTimeFormat
            .forPattern("yyyy-MM-dd HH:mm Z")
            .withZone(NEW_YORK);

    @Test
    void preservesTheCustomerTimeWhileTheUtcInstantTracksDaylightSaving() {
        LocalDateTime saturdayAppointment = new LocalDateTime(2025, 3, 8, 9, 30);
        DateTime saturdayInstant = saturdayAppointment.toDateTime(NEW_YORK);
        DateTime sundayInstant = saturdayInstant.plusDays(1);

        assertEquals("2025-03-08 09:30 -0500", APPOINTMENT_DISPLAY.print(saturdayInstant));
        assertEquals("2025-03-09 09:30 -0400", APPOINTMENT_DISPLAY.print(sundayInstant));
        assertEquals(9, sundayInstant.getHourOfDay());
        assertEquals(30, sundayInstant.getMinuteOfHour());
        assertEquals(23 * 60 * 60 * 1000L,
                sundayInstant.getMillis() - saturdayInstant.getMillis());
    }
}
