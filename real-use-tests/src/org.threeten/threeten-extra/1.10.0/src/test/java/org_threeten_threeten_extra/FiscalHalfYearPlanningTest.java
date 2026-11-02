/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten_threeten_extra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.threeten.extra.Half;
import org.threeten.extra.YearHalf;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the half-year reporting subsystem to plan fiscal periods across a year boundary.
public class FiscalHalfYearPlanningTest {
    @Test
    @Timeout(60)
    public void plansFiscalHalvesAcrossTheYearBoundary() {
        YearHalf closingHalf = YearHalf.parse("2024-H2");
        YearHalf nextClosingHalf = closingHalf.plusHalves(1);
        List<YearHalf> forecastHalves = closingHalf
                .halvesUntil(closingHalf.plusHalves(3))
                .toList();

        assertEquals(Half.H2, closingHalf.getHalf());
        assertEquals(2024, closingHalf.getYear());
        assertEquals(LocalDate.of(2024, 7, 1), closingHalf.atDay(1));
        assertEquals(LocalDate.of(2024, 12, 31), closingHalf.atEndOfHalf());
        assertEquals(184, closingHalf.lengthOfHalf());
        assertTrue(closingHalf.isLeapYear());
        assertEquals(Half.H2, Half.ofMonth(11));

        assertEquals("2025-H1", nextClosingHalf.toString());
        assertEquals(
                List.of(
                        YearHalf.parse("2024-H2"),
                        YearHalf.parse("2025-H1"),
                        YearHalf.parse("2025-H2")),
                forecastHalves);
    }
}
