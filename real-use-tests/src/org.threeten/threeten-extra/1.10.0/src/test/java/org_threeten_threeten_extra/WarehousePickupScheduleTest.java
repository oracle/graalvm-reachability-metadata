/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten_threeten_extra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.threeten.extra.HourMinute;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the hour-and-minute scheduling subsystem to build a warehouse pickup timetable.
public class WarehousePickupScheduleTest {
    @Test
    @Timeout(60)
    public void buildsPickupTimetableAcrossTheEndOfDay() {
        HourMinute receivingOpens = HourMinute.parse("08:30");
        HourMinute loadingStarts = receivingOpens.plusMinutes(50);
        HourMinute pickupWindow = loadingStarts.plusHours(2).plusMinutes(40);
        HourMinute overnightDispatch = HourMinute.parse("23:55").plusMinutes(10);

        assertEquals(HourMinute.of(8, 30), receivingOpens);
        assertEquals(HourMinute.of(9, 20), loadingStarts);
        assertEquals(HourMinute.of(12, 0), pickupWindow);
        assertEquals(450, loadingStarts.until(
                HourMinute.of(16, 50), ChronoUnit.MINUTES));
        assertTrue(pickupWindow.isAfter(loadingStarts));

        assertEquals(
                LocalDateTime.of(2025, 4, 21, 12, 0),
                pickupWindow.atDate(LocalDate.of(2025, 4, 21)));
        assertEquals(
                OffsetTime.of(12, 0, 0, 0, ZoneOffset.ofHours(-4)),
                pickupWindow.atOffset(ZoneOffset.ofHours(-4)));
        assertEquals(
                "12:00",
                pickupWindow.format(DateTimeFormatter.ofPattern("HH:mm")));
        assertEquals(HourMinute.of(0, 5), overnightDispatch);
    }
}
