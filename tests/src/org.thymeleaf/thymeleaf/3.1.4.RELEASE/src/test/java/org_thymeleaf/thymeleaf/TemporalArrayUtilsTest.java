/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.thymeleaf.util.temporal.TemporalArrayUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class TemporalArrayUtilsTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void arrayFormatWithPatternCreatesAStringArrayForTemporalValues() {
        TemporalArrayUtils temporalArrayUtils = new TemporalArrayUtils(Locale.US, UTC);
        Object[] temporalValues = {
                LocalDateTime.of(1981, 6, 15, 13, 45, 12),
                LocalDate.of(2024, 2, 29),
                null
        };

        String[] result = temporalArrayUtils.arrayFormat(temporalValues, "dd/MM/yyyy");

        assertThat(result).isExactlyInstanceOf(String[].class);
        assertThat(result).containsExactly("15/06/1981", "29/02/2024", null);
    }

    @Test
    void arrayYearCreatesAnIntegerArrayForTemporalValues() {
        TemporalArrayUtils temporalArrayUtils = new TemporalArrayUtils(Locale.US, UTC);
        Object[] temporalValues = {
                LocalDate.of(1981, 6, 15),
                LocalDateTime.of(2024, 2, 29, 23, 59, 58, 123_456_789),
                null
        };

        Integer[] result = temporalArrayUtils.arrayYear(temporalValues);

        assertThat(result).isExactlyInstanceOf(Integer[].class);
        assertThat(result).containsExactly(1981, 2024, null);
    }
}
