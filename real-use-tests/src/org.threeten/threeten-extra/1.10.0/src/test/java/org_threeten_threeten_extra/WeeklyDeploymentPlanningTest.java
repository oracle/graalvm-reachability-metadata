/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten_threeten_extra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.threeten.extra.YearWeek;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the ISO week scheduling subsystem to plan a deployment across a year boundary.
public class WeeklyDeploymentPlanningTest {
    @Test
    @Timeout(60)
    public void plansDeploymentWeeksAcrossTheIsoYearBoundary() {
        YearWeek releaseWeek = YearWeek.parse("2024-W52");
        YearWeek validationWeek = releaseWeek.plusWeeks(1);
        YearWeek rolloutWeek = validationWeek.plusWeeks(1);

        assertEquals(2024, releaseWeek.getYear());
        assertEquals(52, releaseWeek.getWeek());
        assertFalse(releaseWeek.is53WeekYear());
        assertEquals(LocalDate.of(2024, 12, 23), releaseWeek.atDay(DayOfWeek.MONDAY));
        assertEquals(LocalDate.of(2024, 12, 29), releaseWeek.atDay(DayOfWeek.SUNDAY));

        assertEquals(YearWeek.parse("2025-W01"), validationWeek);
        assertEquals(LocalDate.of(2025, 1, 3), validationWeek.atDay(DayOfWeek.FRIDAY));
        assertEquals("2025-W02", rolloutWeek.toString());
        assertTrue(rolloutWeek.isAfter(validationWeek));
        assertEquals(YearWeek.parse("2025-W01"), YearWeek.from(LocalDate.of(2024, 12, 31)));

        YearWeek leapWeek = YearWeek.parse("2020-W53");
        assertTrue(leapWeek.is53WeekYear());
        assertEquals(LocalDate.of(2021, 1, 3), leapWeek.atDay(DayOfWeek.SUNDAY));
    }
}
