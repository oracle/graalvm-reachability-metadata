/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten_threeten_extra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.threeten.extra.chrono.JulianChronology;
import org.threeten.extra.chrono.JulianDate;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the alternate-calendar chronology subsystem to convert historical Julian dates for an archive.
public class HistoricalCalendarConversionTest {
    @Test
    @Timeout(60)
    public void convertsJulianArchiveDatesAndKeepsCalendarArithmetic() {
        LocalDate publishedOnIsoCalendar = LocalDate.of(2024, 3, 13);
        JulianDate filingDate = JulianChronology.INSTANCE.date(publishedOnIsoCalendar);
        JulianDate reviewDeadline = filingDate.plus(3, ChronoUnit.DAYS);

        assertEquals(JulianDate.of(2024, 2, 29), filingDate);
        assertEquals(2024, filingDate.get(ChronoField.YEAR));
        assertEquals(2, filingDate.get(ChronoField.MONTH_OF_YEAR));
        assertEquals(29, filingDate.get(ChronoField.DAY_OF_MONTH));
        assertEquals("Julian", filingDate.getChronology().getId());
        assertTrue(filingDate.getChronology().isLeapYear(2024));

        assertEquals(JulianDate.of(2024, 3, 3), reviewDeadline);
        assertEquals(LocalDate.of(2024, 3, 16), LocalDate.ofEpochDay(reviewDeadline.toEpochDay()));
    }
}
