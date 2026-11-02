/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time_joda_time;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.chrono.BuddhistChronology;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Joda-Time's alternate-calendar chronology subsystem for a retail service that exchanges Buddhist and ISO dates.
public class BuddhistCalendarChronologyTest {
    private static final DateTimeZone UTC = DateTimeZone.UTC;
    private static final BuddhistChronology BUDDHIST_UTC = BuddhistChronology.getInstanceUTC();
    private static final DateTimeFormatter THAI_DATE_TIME = DateTimeFormat
            .forPattern("yyyy-MM-dd HH:mm")
            .withChronology(BUDDHIST_UTC)
            .withZone(UTC);

    @Test
    void convertsAStoreDateBetweenBuddhistAndIsoCalendars() {
        DateTime storeAppointment = THAI_DATE_TIME.parseDateTime("2568-04-13 09:00");
        DateTime isoAppointment = storeAppointment.withChronology(ISOChronology.getInstanceUTC());
        DateTime nextStoreAppointment = storeAppointment.plusMonths(1);

        assertEquals(2568, storeAppointment.getYear());
        assertEquals("2568-04-13 09:00", THAI_DATE_TIME.print(storeAppointment));
        assertEquals("2025-04-13T09:00:00.000Z", isoAppointment.toString());
        assertEquals(2025, isoAppointment.getYear());
        assertEquals("2568-05-13 09:00", THAI_DATE_TIME.print(nextStoreAppointment));
        assertEquals(isoAppointment.plusMonths(1).getMillis(), nextStoreAppointment.getMillis());
    }
}
