/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten.threeten_extra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Period;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.threeten.extra.AmountFormats;

public class AmountFormatsTest {
    @Test
    public void formatsPeriodWithWordBasedResourceBundle() {
        Period period = Period.of(1, 2, 10);

        String formatted = AmountFormats.wordBased(period, Locale.ROOT);

        assertThat(formatted).isEqualTo("1 year, 2 months and 10 days");
    }

    @Test
    public void formatsDurationWithWordBasedResourceBundle() {
        Duration duration = Duration.ofHours(2)
                .plusMinutes(1)
                .plusSeconds(3)
                .plusMillis(4);

        String formatted = AmountFormats.wordBased(duration, Locale.ROOT);

        assertThat(formatted).isEqualTo("2 hours, 1 minute, 3 seconds and 4 milliseconds");
    }

    @Test
    public void formatsCombinedPeriodAndDurationWithWordBasedResourceBundle() {
        Period period = Period.of(1, 2, 3);
        Duration duration = Duration.ofHours(52)
                .plusMinutes(4)
                .plusSeconds(5)
                .plusMillis(6);

        String formatted = AmountFormats.wordBased(period, duration, Locale.ROOT);

        assertThat(formatted).isEqualTo("1 year, 2 months, 5 days, 4 hours, 4 minutes, 5 seconds and 6 milliseconds");
    }

    @Test
    public void appliesLocaleSpecificPredicateBasedForms() {
        Locale polish = Locale.forLanguageTag("pl");
        Period period = Period.of(1, 2, 21);

        String formatted = AmountFormats.wordBased(period, polish);

        assertThat(formatted).isEqualTo("1 rok, 2 miesi\u0105ce i 3 tygodnie");
    }
}
