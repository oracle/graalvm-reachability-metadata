/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.thymeleaf.util.temporal.TemporalArrayUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class TemporalArrayUtilsTest {

    @Test
    void arrayFormatCreatesStringArrayForTemporalValues() {
        TemporalArrayUtils temporalArrayUtils = new TemporalArrayUtils(Locale.US, ZoneId.of("UTC"));
        Object[] values = {
                LocalDate.of(2024, 1, 2),
                LocalDate.of(2025, 12, 31),
                null
        };

        String[] result = temporalArrayUtils.arrayFormat(values, "dd MMMM uuuu", Locale.US);

        assertThat(result)
                .isInstanceOf(String[].class)
                .containsExactly("02 January 2024", "31 December 2025", null);
    }

    @Test
    void arrayDayCreatesIntegerArrayForTemporalValues() {
        TemporalArrayUtils temporalArrayUtils = new TemporalArrayUtils(Locale.US, ZoneId.of("UTC"));
        Object[] values = {
                LocalDate.of(2024, 1, 2),
                LocalDate.of(2025, 12, 31),
                null
        };

        Integer[] result = temporalArrayUtils.arrayDay(values);

        assertThat(result)
                .isInstanceOf(Integer[].class)
                .containsExactly(2, 31, null);
    }
}
