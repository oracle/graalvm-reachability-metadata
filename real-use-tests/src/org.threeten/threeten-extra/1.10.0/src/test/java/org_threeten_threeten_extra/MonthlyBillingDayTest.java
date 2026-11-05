/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten_threeten_extra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.threeten.extra.DayOfMonth;

import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the day-of-month subsystem to schedule recurring monthly billing dates.
public class MonthlyBillingDayTest {
    @Test
    @Timeout(60)
    public void schedulesBillingDayAcrossMonthlyCycles() {
        DayOfMonth billingDay = DayOfMonth.of(15);
        LocalDate orderPlaced = LocalDate.of(2025, 1, 15);
        DayOfMonth dayFromOrder = DayOfMonth.from(orderPlaced);
        YearMonth nextBillingMonth = YearMonth.of(2025, 2);

        LocalDate nextBillingDate = billingDay.atYearMonth(nextBillingMonth);
        LocalDate adjustedOrderDate = orderPlaced.with(billingDay);
        MonthDay marchReminder = billingDay.atMonth(Month.MARCH);

        assertEquals(15, billingDay.getValue());
        assertEquals(billingDay, dayFromOrder);
        assertTrue(billingDay.isValidYearMonth(nextBillingMonth));
        assertEquals(LocalDate.of(2025, 2, 15), nextBillingDate);
        assertEquals(orderPlaced, adjustedOrderDate);
        assertEquals(MonthDay.of(Month.MARCH, 15), marchReminder);
    }
}
