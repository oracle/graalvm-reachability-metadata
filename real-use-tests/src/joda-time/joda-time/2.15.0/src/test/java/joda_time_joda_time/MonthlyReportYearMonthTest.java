/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time_joda_time;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.YearMonth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Joda-Time's year-month subsystem for defining monthly reporting windows without storing a day or clock time.
public class MonthlyReportYearMonthTest {
    private static final DateTimeZone UTC = DateTimeZone.UTC;

    @Test
    void definesTheMonthlyReportWindowAndAdvancesToTheNextMonth() {
        YearMonth reportMonth = YearMonth.parse("2025-01");
        YearMonth nextReportMonth = reportMonth.plusMonths(1);
        Interval reportWindow = reportMonth.toInterval(UTC);
        DateTime reportStart = new DateTime(2025, 1, 1, 0, 0, UTC);
        DateTime nextReportStart = new DateTime(2025, 2, 1, 0, 0, UTC);

        assertEquals("2025-01", reportMonth.toString());
        assertEquals(2025, reportMonth.getYear());
        assertEquals(1, reportMonth.getMonthOfYear());
        assertEquals("2025-02", nextReportMonth.toString());
        assertEquals(reportStart, reportWindow.getStart());
        assertEquals(nextReportStart, reportWindow.getEnd());
        assertEquals(31L * 24 * 60 * 60 * 1000, reportWindow.toDurationMillis());
        assertEquals(reportStart, reportMonth.toLocalDate(1).toDateTimeAtStartOfDay(UTC));
    }
}
