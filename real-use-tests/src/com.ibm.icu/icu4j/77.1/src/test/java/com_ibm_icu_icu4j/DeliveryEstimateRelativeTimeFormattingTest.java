/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu_icu4j;

import com.ibm.icu.text.RelativeDateTimeFormatter;
import com.ibm.icu.util.ULocale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ICU4J's relative date-time formatting subsystem for delivery estimates.
public class DeliveryEstimateRelativeTimeFormattingTest {
    @Test
    public void formatsDeliveryEstimatesForDifferentLocales() {
        DeliveryEstimate estimate = new DeliveryEstimate();

        assertEquals("tomorrow", estimate.tomorrow(ULocale.ENGLISH));
        assertEquals("in 2 days", estimate.inDays(ULocale.ENGLISH, 2));
        assertEquals("dans 3 heures", estimate.inHours(ULocale.FRENCH, 3));
    }

    private static final class DeliveryEstimate {
        private String tomorrow(ULocale locale) {
            RelativeDateTimeFormatter formatter = RelativeDateTimeFormatter.getInstance(locale);
            return formatter.format(
                    RelativeDateTimeFormatter.Direction.NEXT,
                    RelativeDateTimeFormatter.AbsoluteUnit.DAY);
        }

        private String inDays(ULocale locale, int days) {
            RelativeDateTimeFormatter formatter = RelativeDateTimeFormatter.getInstance(locale);
            return formatter.format(
                    days,
                    RelativeDateTimeFormatter.Direction.NEXT,
                    RelativeDateTimeFormatter.RelativeUnit.DAYS);
        }

        private String inHours(ULocale locale, int hours) {
            RelativeDateTimeFormatter formatter = RelativeDateTimeFormatter.getInstance(locale);
            return formatter.format(
                    hours,
                    RelativeDateTimeFormatter.Direction.NEXT,
                    RelativeDateTimeFormatter.RelativeUnit.HOURS);
        }
    }
}
