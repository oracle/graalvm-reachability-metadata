/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten.threeten_extra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.temporal.JulianFields;

import org.junit.jupiter.api.Test;
import org.threeten.extra.scale.TaiInstant;
import org.threeten.extra.scale.UtcInstant;
import org.threeten.extra.scale.UtcRules;

public class SystemUtcRulesTest {
    private static final long NANOS_PER_STANDARD_DAY = 86_400_000_000_000L;

    @Test
    public void loadsSystemLeapSecondRulesFromClasspathResources() {
        UtcRules rules = UtcRules.system();
        long leapSecondDay = modifiedJulianDay(LocalDate.of(2016, 12, 31));
        long dayAfterLeapSecond = modifiedJulianDay(LocalDate.of(2017, 1, 1));

        assertThat(rules.getName()).isEqualTo("System");
        assertThat(rules.getLeapSecondDates()).contains(leapSecondDay);
        assertThat(rules.getLeapSecondAdjustment(leapSecondDay)).isEqualTo(1);
        assertThat(rules.getTaiOffset(dayAfterLeapSecond)).isEqualTo(37);
    }

    @Test
    public void roundTripsUtcLeapSecondThroughTaiUsingSystemRules() {
        UtcRules rules = UtcRules.system();
        long leapSecondDay = modifiedJulianDay(LocalDate.of(2016, 12, 31));
        UtcInstant leapSecond = UtcInstant.ofModifiedJulianDay(leapSecondDay, NANOS_PER_STANDARD_DAY);

        TaiInstant taiInstant = rules.convertToTai(leapSecond);
        UtcInstant converted = rules.convertToUtc(taiInstant);

        assertThat(converted).isEqualTo(leapSecond);
    }

    private static long modifiedJulianDay(LocalDate date) {
        return date.getLong(JulianFields.MODIFIED_JULIAN_DAY);
    }
}
