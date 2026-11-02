/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten_threeten_extra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.threeten.extra.DayOfYear;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the day-of-year scheduling subsystem to place an annual maintenance window in leap and common years.
public class AnnualMaintenanceDayTest {
    @Test
    @Timeout(60)
    public void schedulesAnnualMaintenanceAcrossLeapYearBoundaries() {
        DayOfYear maintenanceDay = DayOfYear.of(60);
        DayOfYear leapYearFinalDay = DayOfYear.of(366);
        LocalDate leapYearDate = maintenanceDay.atYear(2024);
        LocalDate commonYearDate = maintenanceDay.atYear(2025);
        LocalDate configuredDate = LocalDate.of(2025, 1, 1).with(maintenanceDay);

        assertEquals(60, maintenanceDay.getValue());
        assertEquals(maintenanceDay, DayOfYear.from(leapYearDate));
        assertEquals(LocalDate.of(2024, 2, 29), leapYearDate);
        assertEquals(LocalDate.of(2025, 3, 1), commonYearDate);
        assertEquals(commonYearDate, configuredDate);
        assertTrue(leapYearFinalDay.isValidYear(2024));
        assertFalse(leapYearFinalDay.isValidYear(2025));
        assertEquals(LocalDate.of(2024, 12, 31), leapYearFinalDay.atYear(2024));
    }
}
