/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten_threeten_extra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.threeten.extra.AmountFormats;
import org.threeten.extra.PeriodDuration;

import java.time.Duration;
import java.time.Period;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises the human-readable amount-formatting subsystem to present service-plan terms.
public class ServicePlanAmountFormattingTest {
    @Test
    @Timeout(60)
    public void formatsAndParsesServicePlanTerms() {
        Period includedCalendarTime = Period.of(1, 2, 3);
        Duration includedClockTime = Duration.ofHours(4).plusMinutes(5).plusSeconds(6);
        PeriodDuration planTerm = PeriodDuration.of(includedCalendarTime, includedClockTime);

        assertEquals(
                "1 year, 2 months, 3 days, 4 hours, 5 minutes and 6 seconds",
                AmountFormats.wordBased(planTerm, Locale.US));
        assertEquals(
                "P1Y2M3DT4H5M6S",
                AmountFormats.iso8601(includedCalendarTime, includedClockTime));
        assertEquals(
                Duration.ofHours(2).plusMinutes(30).plusSeconds(15).plusMillis(500),
                AmountFormats.parseUnitBasedDuration("2h30m15.5s"));
    }
}
