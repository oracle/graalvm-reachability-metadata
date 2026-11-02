/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten_threeten_extra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.threeten.extra.Temporals;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjuster;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises the working-day adjustment subsystem to schedule invoice processing around a weekend.
public class WorkingDaySchedulingTest {
    @Test
    @Timeout(60)
    public void movesInvoiceDatesToWorkingDays() {
        LocalDate submittedOnFriday = LocalDate.of(2025, 3, 14);
        LocalDate submittedOnSaturday = submittedOnFriday.plusDays(1);
        TemporalAdjuster nextWorkingDay = Temporals.nextWorkingDay();
        TemporalAdjuster nextWorkingDayOrSame = Temporals.nextWorkingDayOrSame();
        TemporalAdjuster previousWorkingDay = Temporals.previousWorkingDay();
        TemporalAdjuster previousWorkingDayOrSame = Temporals.previousWorkingDayOrSame();

        assertEquals(
                LocalDate.of(2025, 3, 17),
                submittedOnFriday.with(nextWorkingDay));
        assertEquals(
                submittedOnFriday,
                submittedOnFriday.with(nextWorkingDayOrSame));
        assertEquals(
                LocalDate.of(2025, 3, 17),
                submittedOnSaturday.with(nextWorkingDayOrSame));
        assertEquals(
                LocalDate.of(2025, 3, 14),
                LocalDate.of(2025, 3, 17).with(previousWorkingDay));
        assertEquals(
                LocalDate.of(2025, 3, 17),
                LocalDate.of(2025, 3, 17).with(previousWorkingDayOrSame));
    }
}
