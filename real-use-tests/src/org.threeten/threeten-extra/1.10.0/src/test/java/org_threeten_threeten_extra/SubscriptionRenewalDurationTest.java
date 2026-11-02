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
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises the composite duration subsystem to present subscription renewal terms.
public class SubscriptionRenewalDurationTest {
    @Test
    @Timeout(60)
    public void combinesCalendarAndClockTimeForRenewalTerms() {
        PeriodDuration standardTerm = PeriodDuration.parse("P1M2DT3H15M");
        Duration customerExtension = AmountFormats.parseUnitBasedDuration("90m");
        PeriodDuration extendedTerm = standardTerm.plus(customerExtension);
        LocalDateTime renewalStart = LocalDateTime.of(2025, 1, 31, 10, 0);

        assertEquals(Period.ofMonths(1).plusDays(2), standardTerm.getPeriod());
        assertEquals(Duration.ofHours(3).plusMinutes(15), standardTerm.getDuration());
        assertEquals(
                LocalDateTime.of(2025, 3, 2, 13, 15),
                standardTerm.addTo(renewalStart));
        assertEquals("1 month, 2 days, 3 hours and 15 minutes", AmountFormats.wordBased(
                standardTerm,
                Locale.ENGLISH));

        assertEquals("P1M2DT4H45M", extendedTerm.toString());
        assertEquals("1 month, 2 days, 4 hours and 45 minutes", AmountFormats.wordBased(
                extendedTerm,
                Locale.ENGLISH));
        assertEquals(
                LocalDateTime.of(2025, 3, 2, 14, 45),
                extendedTerm.addTo(renewalStart));
    }
}
