/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time.joda_time;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.DateFormatSymbols;
import java.util.Locale;

import org.joda.time.DateTimeUtils;
import org.junit.jupiter.api.Test;

public class DateTimeUtilsTest {

    @Test
    void getsDateFormatSymbolsForLocale() {
        DateFormatSymbols symbols = DateTimeUtils.getDateFormatSymbols(Locale.US);

        assertThat(symbols.getZoneStrings()).isNotEmpty();
    }
}
