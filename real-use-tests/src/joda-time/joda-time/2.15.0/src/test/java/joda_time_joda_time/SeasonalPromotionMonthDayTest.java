/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time_joda_time;

import org.joda.time.LocalDate;
import org.joda.time.MonthDay;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Joda-Time's month-day subsystem for scheduling a recurring leap-day promotion.
public class SeasonalPromotionMonthDayTest {
    @Test
    void schedulesThePromotionWithoutStoringAnArbitraryYear() {
        MonthDay promotionStart = MonthDay.parse("--02-28");
        MonthDay promotionEnd = promotionStart.plusDays(1);
        LocalDate promotionInLeapYear = promotionEnd.toLocalDate(2024);
        LocalDate promotionInCommonYear = promotionStart.toLocalDate(2025);

        assertEquals("--02-28", promotionStart.toString());
        assertEquals("--02-29", promotionEnd.toString());
        assertEquals(new LocalDate(2024, 2, 29), promotionInLeapYear);
        assertEquals(new LocalDate(2025, 2, 28), promotionInCommonYear);
        assertEquals(2, promotionEnd.getMonthOfYear());
        assertEquals(29, promotionEnd.getDayOfMonth());
    }
}
