/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten_threeten_extra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.threeten.extra.Quarter;
import org.threeten.extra.YearQuarter;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the quarterly reporting-period subsystem to schedule a year-end forecast.
public class QuarterlyReportingPeriodTest {
    @Test
    @Timeout(60)
    public void buildsQuarterlyForecastAcrossYearBoundary() {
        YearQuarter closingQuarter = YearQuarter.parse("2024-Q4");
        YearQuarter nextClosingQuarter = closingQuarter.plusQuarters(1);
        List<YearQuarter> forecastQuarters = closingQuarter
                .quartersUntil(closingQuarter.plusQuarters(4))
                .toList();

        assertEquals(Quarter.Q4, closingQuarter.getQuarter());
        assertEquals(2024, closingQuarter.getYear());
        assertEquals(LocalDate.of(2024, 10, 1), closingQuarter.atDay(1));
        assertEquals(LocalDate.of(2024, 12, 31), closingQuarter.atEndOfQuarter());
        assertEquals("2025-Q1", nextClosingQuarter.toString());
        assertTrue(closingQuarter.isLeapYear());
        assertEquals(
                List.of(
                        YearQuarter.parse("2024-Q4"),
                        YearQuarter.parse("2025-Q1"),
                        YearQuarter.parse("2025-Q2"),
                        YearQuarter.parse("2025-Q3")),
                forecastQuarters);
    }
}
